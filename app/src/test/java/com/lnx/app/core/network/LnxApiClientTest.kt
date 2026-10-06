package com.lnx.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 认证接口的**协议层**测试(v0.3 A3):用 MockEngine 假装服务器,
 * 锁四件事 —— 路径、方法、Bearer 头、错误体到 [LnxApiException] 的翻译。
 * 请求体字段的正确性由真服务器端到端(A4 起)和协议两边逐字对齐的约定兜底,
 * MockEngine 里再去拆序列化字节,测的是 Ktor 自己的实现,不是我们的代码。
 */
class LnxApiClientTest {

    private val okAuth = """
        {"token":"tok-123","expiresAt":1791200000000,
         "user":{"id":"u-1","username":"alice"}}
    """.trimIndent().replace("\n", "")

    private fun client(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(lnxJson()) }
        }

    private fun jsonResponding(status: HttpStatusCode, body: String): MockRequestHandler = { request ->
        // 请求只进不出:MockEngine 的应答由测试逐个给,这里顺手记录路径/方法供断言
        respondedPaths += request.url.encodedPath
        respondedMethods += request.method.value
        respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val respondedPaths = mutableListOf<String>()
    private val respondedMethods = mutableListOf<String>()

    @Test
    fun `登录打的是 v1 auth login 并解析出令牌`() = runTest {
        respondedPaths.clear()
        val api = LnxApiClient(client(jsonResponding(HttpStatusCode.OK, okAuth)))

        val auth = api.login("https://s.example", "alice", "password123")

        assertEquals("/v1/auth/login", respondedPaths.single())
        assertEquals("POST", respondedMethods.single())
        assertEquals("tok-123", auth.token)
        assertEquals("alice", auth.user.username)
        assertEquals(1791200000000L, auth.expiresAt)
    }

    @Test
    fun `注册打的是 v1 auth register`() = runTest {
        respondedPaths.clear()
        val api = LnxApiClient(client(jsonResponding(HttpStatusCode.Created, okAuth)))

        val auth = api.register("https://s.example", "alice", "password123", "INVITE-1")

        assertEquals("/v1/auth/register", respondedPaths.single())
        assertEquals("tok-123", auth.token)
    }

    @Test
    fun `me 带的是 Bearer 头`() = runTest {
        var seenAuth: String? = null
        val api = LnxApiClient(
            client { request ->
                seenAuth = request.headers[HttpHeaders.Authorization]
                respondedPaths += request.url.encodedPath
                respond(
                    content = """{"user":{"id":"u-1","username":"alice"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        api.me("https://s.example", "tok-123")

        assertEquals("/v1/auth/me", respondedPaths.single())
        assertEquals("Bearer tok-123", seenAuth)
    }

    @Test
    fun `服务端错误体翻成 LnxApiException 并保留稳定 code`() = runTest {
        val api = LnxApiClient(
            client(
                jsonResponding(
                    HttpStatusCode.BadRequest,
                    """{"error":{"code":"invite_required","message":"邀请码不对"}}""",
                ),
            ),
        )

        val e = assertThrows(LnxApiException::class.java) {
            runBlocking {
                api.register("https://s.example", "alice", "password123", "wrong")
            }
        }
        assertEquals("invite_required", e.code)
        assertEquals(400, e.httpStatus)
    }

    @Test
    fun `429 锁定的 code 是 locked_out`() = runTest {
        val api = LnxApiClient(
            client(
                jsonResponding(
                    HttpStatusCode.TooManyRequests,
                    """{"error":{"code":"locked_out","message":"密码错的次数太多"}}""",
                ),
            ),
        )

        val e = assertThrows(LnxApiException::class.java) {
            runBlocking { api.login("https://s.example", "alice", "password123") }
        }
        assertEquals("locked_out", e.code)
    }

    @Test
    fun `错误体缺失时退回 http 状态码`() = runTest {
        val api = LnxApiClient(
            client(jsonResponding(HttpStatusCode.InternalServerError, "<html>oops</html>")),
        )

        val e = assertThrows(LnxApiException::class.java) {
            runBlocking { api.login("https://s.example", "alice", "password123") }
        }
        assertEquals("http_500", e.code)
    }

    @Test
    fun `断网翻成 unreachable 而不是裸异常`() {
        // 直接测映射函数:MockEngine 里抛 IOException 会被引擎再包一层,
        // 那测出来的是 Ktor 的包装行为,不是我们的规则
        val e = apiExceptionFrom(java.net.ConnectException("connection refused"), null)
        assertEquals("unreachable", e.code)
        assertTrue(e.cause is java.net.ConnectException)
    }
}
