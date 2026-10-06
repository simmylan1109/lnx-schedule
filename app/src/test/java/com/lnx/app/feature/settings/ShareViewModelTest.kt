package com.lnx.app.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.network.LnxApiClient
import com.lnx.app.core.network.SyncApi
import com.lnx.app.core.network.SyncRowDto
import com.lnx.app.core.network.TokenStorage
import com.lnx.app.core.network.lnxJson
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import com.lnx.app.core.sync.LocalSyncStore
import com.lnx.app.core.sync.SyncEngine
import com.lnx.app.core.sync.SyncStateStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.File
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 共享段的状态机(v0.3 A5):登录状态四态、登出"本地必清"、立即同步写账本。
 * 全部 JVM 假件:me() 走 MockEngine,同步引擎用空脚本假件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

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

        override suspend fun setAutoSync(enabled: Boolean) {
            state.value = state.value.copy(autoSync = enabled)
        }

        override suspend fun setLastExportAt(millis: Long) = Unit
    }

    /** 引擎的窄口假件:空拉、空推 —— 只要"同步成功"这条路走通 */
    private class NoopSyncApi : SyncApi {
        override suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int) =
            com.lnx.app.core.network.PullResponse(rows = emptyList(), cursor = 1, hasMore = false)

        override suspend fun push(baseUrl: String, token: String, rows: List<SyncRowDto>) =
            com.lnx.app.core.network.PushResponse(results = emptyList())
    }

    private class NoopStore : LocalSyncStore {
        override suspend fun currentRows(): List<SyncRowDto> = emptyList()
        override suspend fun applyPulled(rows: List<SyncRowDto>) =
            com.lnx.app.core.sync.AppliedResult(applied = rows, skipped = emptyList())
    }

    private val tokens = RecordingTokens()
    private val settingsRepo = FakeSettings()

    private fun apiResponding(status: HttpStatusCode, body: String): LnxApiClient =
        LnxApiClient(
            HttpClient(
                MockEngine {
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

    private fun stateStore(): SyncStateStore =
        SyncStateStore(PreferenceDataStoreFactory.create { File(tmp.newFolder(), "sync-state.preferences_pb") })

    private fun vmWith(api: LnxApiClient): ShareViewModel {
        // 引擎和 VM 必须共用同一个账本文件 —— 生产里它是单例,各拿一个的话
        // 引擎写的 lastSyncAt 在 VM 那边永远读不到
        val state = stateStore()
        val engine = SyncEngine(NoopSyncApi(), NoopStore(), state)
        return ShareViewModel(settingsRepo, tokens, api, engine, state)
    }

    /** 轮询等状态离开 Checking(挂起落在哪个调度器上是 Ktor 的实现细节) */
    private suspend fun TestScope.awaitAccount(vm: ShareViewModel) {
        repeat(200) {
            if (vm.account.value != ShareViewModel.AccountUi.Checking) return
            testScheduler.advanceUntilIdle()
            dispatcher.scheduler.advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(25) }
        }
    }

    private val meOk = """{"user":{"id":"u-1","username":"alice"}}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `没配地址或没令牌_直接未登录`() = runTest(dispatcher) {
        val vm = vmWith(apiResponding(HttpStatusCode.OK, meOk))
        awaitAccount(vm)
        assertEquals(ShareViewModel.AccountUi.SignedOut, vm.account.value)
    }

    @Test
    fun `有令牌_me返回用户名`() = runTest(dispatcher) {
        tokens.saved = "tok-1"
        settingsRepo.state.value = settingsRepo.state.value.copy(serverBaseUrl = "https://s.example")
        val vm = vmWith(apiResponding(HttpStatusCode.OK, meOk))
        awaitAccount(vm)
        assertEquals(ShareViewModel.AccountUi.SignedIn("alice"), vm.account.value)
    }

    @Test
    fun `me返回401_清掉作废的令牌转未登录`() = runTest(dispatcher) {
        tokens.saved = "tok-stale"
        settingsRepo.state.value = settingsRepo.state.value.copy(serverBaseUrl = "https://s.example")
        val vm = vmWith(
            apiResponding(
                HttpStatusCode.Unauthorized,
                """{"error":{"code":"unauthorized","message":"登录凭据无效或已过期"}}""",
            ),
        )
        awaitAccount(vm)
        assertEquals(ShareViewModel.AccountUi.SignedOut, vm.account.value)
        assertNull("作废令牌要清掉", tokens.saved)
    }

    @Test
    fun `断网问不到_保留已登录不谎称掉线`() = runTest(dispatcher) {
        tokens.saved = "tok-1"
        settingsRepo.state.value = settingsRepo.state.value.copy(serverBaseUrl = "https://s.example")
        // 5xx 同样是"问不出来"而非"没登录"
        val vm = vmWith(apiResponding(HttpStatusCode.InternalServerError, "<html>oops</html>"))
        awaitAccount(vm)
        assertEquals(ShareViewModel.AccountUi.SignedInUnconfirmed, vm.account.value)
        assertEquals("tok-1", tokens.saved)
    }

    @Test
    fun `退出登录_本地必清_服务端吊销尽力而为`() = runTest(dispatcher) {
        tokens.saved = "tok-1"
        settingsRepo.state.value = settingsRepo.state.value.copy(serverBaseUrl = "https://s.example")
        var logoutCalled = false
        val api = LnxApiClient(
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/v1/auth/logout")) logoutCalled = true
                    respond(content = "", status = HttpStatusCode.NoContent)
                },
            ) { expectSuccess = true },
        )
        val vm = vmWith(api)
        awaitAccount(vm)
        vm.signOut()
        awaitSettledUntil(vm) { vm.account.value == ShareViewModel.AccountUi.SignedOut }
        assertTrue("服务端应收到登出请求", logoutCalled)
        assertNull(tokens.saved)
    }

    @Test
    fun `立即同步_账本写入上次同步时间`() = runTest(dispatcher) {
        tokens.saved = "tok-1"
        settingsRepo.state.value = settingsRepo.state.value.copy(serverBaseUrl = "https://s.example")
        val vm = vmWith(apiResponding(HttpStatusCode.OK, meOk))
        awaitAccount(vm)

        vm.syncNow()
        awaitSettledUntil(vm) { vm.syncState.value.lastSyncAt > 0L }
        assertTrue(vm.syncState.value.lastSyncAt > 0L)
        assertNull(vm.syncState.value.lastError)
    }

    private suspend fun TestScope.awaitSettledUntil(vm: ShareViewModel, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            testScheduler.advanceUntilIdle()
            dispatcher.scheduler.advanceUntilIdle()
            withContext(Dispatchers.Default) { delay(25) }
        }
    }
}