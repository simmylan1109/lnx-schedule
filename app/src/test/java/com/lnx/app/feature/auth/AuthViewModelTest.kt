package com.lnx.app.feature.auth

import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.network.LnxApiClient
import com.lnx.app.core.network.LnxApiException
import com.lnx.app.core.network.TokenStorage
import com.lnx.app.core.network.lnxJson
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 登录流水线(v0.3 A3):成功存令牌+地址、服务端错误带 code 上浮、
 * **本地校验不过时一个网络包都不发**(MockEngine 里只要被调到就让测试失败)。
 *
 * UnconfinedTestDispatcher 顶掉 Main:viewModelScope 的协程在本测试线程上跑完,
 * 断言不需要等轮转。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class RecordingTokens : TokenStorage {
        var saved: String? = null
        override fun save(token: String) {
            saved = token
        }

        override fun read(): String? = saved
        override fun clear() {
            saved = null
        }
    }

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(SettingsDefaults.snapshot())
        override val settings: Flow<LnxSettings> = state
        override suspend fun current(): LnxSettings = state.value
        override suspend fun setThemeSlot(slot: ThemeSlot) = Unit
        override suspend fun setDarkMode(mode: DarkMode) = Unit
        override suspend fun setReminderLead(minutes: Int?) = Unit
        override suspend fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) = Unit
        override suspend fun setWeekStartMonday(monday: Boolean) = Unit
        override suspend fun setLanguage(language: String) = Unit
        override suspend fun setOnboardingDone() = Unit
        override suspend fun setPrivacyDisclosureDone() = Unit
        override suspend fun setServerBaseUrl(url: String) {
            state.value = state.value.copy(serverBaseUrl = url)
        }

        override suspend fun setLastExportAt(millis: Long) = Unit
    }

    private val tokens = RecordingTokens()
    private val settingsRepo = FakeSettings()

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun apiServing(status: HttpStatusCode, body: String, handlerCalls: MutableList<String> = mutableListOf()): LnxApiClient =
        LnxApiClient(
            HttpClient(
                MockEngine { request ->
                    handlerCalls += request.url.encodedPath
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                },
            ) {
                expectSuccess = true
                install(ContentNegotiation) { json(lnxJson()) }
            },
        )

    /**
     * 等提交流水线走完:HTTP 响应体的读取/解码是真挂起,续延落在哪个调度器上是
     * Ktor 的实现细节,推一遍测试调度器 + 让出真实线程各来几轮,直到结局出现。
     * 上限 200×25ms,防真挂死时测试跟着挂死。
     */
    private suspend fun TestScope.awaitSettled(vm: AuthViewModel) {
        repeat(200) {
            if (vm.outcome.value != null) return
            testScheduler.advanceUntilIdle()
            dispatcher.scheduler.advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(25) }
        }
    }

    private val okAuth =
        """{"token":"tok-1","expiresAt":1791200000000,"user":{"id":"u","username":"alice"}}"""

    @Test
    fun `登录成功_令牌和地址都落库_结局是成功`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        viewModel = AuthViewModel(apiServing(HttpStatusCode.OK, okAuth, calls), tokens, settingsRepo)

        viewModel.submit(AuthMode.LOGIN, "https://s.example/", "alice", "password123", "")
        awaitSettled(viewModel)
        assertEquals("/v1/auth/login", calls.single())
        assertEquals("tok-1", tokens.saved)
        assertEquals("https://s.example", settingsRepo.state.value.serverBaseUrl)
        assertEquals(AuthOutcome.Success, viewModel.outcome.value)
    }

    @Test
    fun `服务端错误_结局失败带稳定code_令牌不入库`() = runTest(dispatcher) {
        viewModel = AuthViewModel(
            apiServing(HttpStatusCode.Unauthorized, """{"error":{"code":"unauthorized","message":"用户名或密码不对"}}"""),
            tokens,
            settingsRepo,
        )

        viewModel.submit(AuthMode.LOGIN, "https://s.example", "alice", "wrong-pass-1", "")
        awaitSettled(viewModel)

        val outcome = viewModel.outcome.value as AuthOutcome.Failure
        assertEquals("unauthorized", outcome.code)
        assertNull(tokens.saved)
    }

    @Test
    fun `地址不以 https 开头_本地就拦_一个网络包都不发`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        viewModel = AuthViewModel(apiServing(HttpStatusCode.OK, okAuth, calls), tokens, settingsRepo)

        viewModel.submit(AuthMode.LOGIN, "http://s.example", "alice", "password123", "")

        assertTrue(calls.isEmpty())
        assertEquals("bad_url", (viewModel.outcome.value as AuthOutcome.Failure).code)
        assertNull(tokens.saved)
    }

    @Test
    fun `密码太短_本地就拦`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        viewModel = AuthViewModel(apiServing(HttpStatusCode.OK, okAuth, calls), tokens, settingsRepo)

        viewModel.submit(AuthMode.REGISTER, "https://s.example", "alice", "short", "INVITE")

        assertTrue(calls.isEmpty())
        assertEquals("weak_password", (viewModel.outcome.value as AuthOutcome.Failure).code)
    }

    @Test
    fun `注册没填邀请码_本地就拦`() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        viewModel = AuthViewModel(apiServing(HttpStatusCode.OK, okAuth, calls), tokens, settingsRepo)

        viewModel.submit(AuthMode.REGISTER, "https://s.example", "alice", "password123", "  ")

        assertTrue(calls.isEmpty())
        assertEquals("invite_required", (viewModel.outcome.value as AuthOutcome.Failure).code)
    }

    @Test
    fun `地址会去掉尾斜杠再入库`() = runTest(dispatcher) {
        viewModel = AuthViewModel(apiServing(HttpStatusCode.OK, okAuth), tokens, settingsRepo)

        viewModel.submit(AuthMode.LOGIN, "https://s.example///", "alice", "password123", "")
        awaitSettled(viewModel)

        assertEquals("https://s.example", settingsRepo.state.value.serverBaseUrl)
    }
}