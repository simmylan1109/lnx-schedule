package com.lnx.app.core.network

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **真服务器**端到端(v0.3 A6):固定证书的真客户端,打真服务器。
 *
 * 两档:
 * - 第一档不需要任何凭据(`me`/`pull` 带假令牌要 401),任何机器上都能跑;
 * - 第二档(注册→登录→推→拉→登出全链路)需要邀请码,从本机 gitignored 的
 *   `local.properties`(`lnx.test.invite`)或环境变量 `LNX_TEST_INVITE` 读 ——
 *   **没有就整条跳过**。邀请码是服务器总钥匙,只有你愿意放进本机文件时这条才跑,
 *   它**绝不入库**(和 `lnx.test.server` 同一条红线)。
 *
 * 全链路会注册一个一次性账号(用户名带时间戳),往库里留几行测试数据 ——
 * 和你的账号完全隔离,是可见的垃圾,不是损坏。
 */
class RealServerE2ETest {

    private fun testUrl(): String? = readProp("lnx.test.server")

    private fun testInvite(): String? = readProp("lnx.test.invite")
        ?: System.getenv("LNX_TEST_INVITE")?.takeIf { it.isNotBlank() }

    private fun readProp(key: String): String? {
        // 环境变量名是大写下划线:lnx.test.server -> LNX_TEST_SERVER(点必须换掉)
        System.getenv(key.uppercase().replace('.', '_'))?.let { if (it.isNotBlank()) return it }
        System.getProperty(key)?.let { if (it.isNotBlank()) return it }
        for (file in listOf(File("local.properties"), File("../local.properties"))) {
            if (!file.isFile) continue
            file.readLines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private val realCert
        get() = CertificatePinner.loadPinnedCert(
            checkNotNull(javaClass.getResourceAsStream("/server_cert.pem")) { "missing test cert" },
        )

    private fun api(): LnxApiClient = LnxApiClient(pinnedHttpClient(realCert))

    @Test
    fun `带假令牌的 me 被 401 拒_且错误码稳定`() {
        val url = testUrl()
        assumeTrue("lnx.test.server not configured - skip", url != null)

        val e = runCatching { runBlocking { api().me(url!!, "definitely-not-a-token") } }
            .exceptionOrNull() as? LnxApiException
            ?: throw AssertionError("expected LnxApiException")

        assertEquals("unauthorized", e.code)
        assertEquals(401, e.httpStatus)
    }

    @Test
    fun `拉取接口同样要令牌`() {
        val url = testUrl()
        assumeTrue("lnx.test.server not configured - skip", url != null)

        val e = runCatching { runBlocking { api().pull(url!!, "definitely-not-a-token", since = 0) } }
            .exceptionOrNull() as? LnxApiException
            ?: throw AssertionError("expected LnxApiException")

        assertEquals("unauthorized", e.code)
    }

    @Test
    fun `全链路_注册登录推拉登出`() {
        val url = testUrl()
        val invite = testInvite()
        assumeTrue("lnx.test.server not configured - skip", url != null)
        assumeTrue("lnx.test.invite not configured - skip", invite != null)
        // assumeTrue 不做智能转换,这里取一个非空的出来,后面全用它
        val base = url!!
        val code = invite!!

        val api = api()
        val username = "e2e-${System.currentTimeMillis() % 100_000_000L}"
        val pwd = "E2e-${System.currentTimeMillis() % 100_000_000L}-ok"

        // 1. 注册(注册即登录)
        val registered = runBlocking { api.register(base, username, pwd, code) }
        assertEquals(username, registered.user.username)
        assertTrue("注册应拿到令牌", registered.token.isNotBlank())

        // 2. me 认得这把钥匙
        val me = runBlocking { api.me(base, registered.token) }
        assertEquals(username, me.user.username)

        // 3. 推一行 → 应被接受(新行,不存在新旧之比)
        val rowId = "e2e-row-$username"
        val now = System.currentTimeMillis()
        val row = SyncRowDto(
            entity = "event",
            id = rowId,
            payload = buildJsonObject {
                put("marker", JsonPrimitive("e2e"))
                put("updatedAt", JsonPrimitive(now))
            },
            updatedAt = now,
            deleted = false,
        )
        val push = runBlocking { api.push(base, registered.token, listOf(row)) }
        val pushResult = push.results.single()
        assertTrue("新行应被接受: ${pushResult.reason}", pushResult.accepted)

        // 4. 拉取(since=0 从头)能拿回自己推的这行 - 证明游标与存储走通
        val pulled = runBlocking { api.pull(base, registered.token, since = 0) }
        assertTrue("拉取应含刚推的行", pulled.rows.any { it.id == rowId })
        assertTrue("游标应被服务端推进", pulled.cursor > 0)

        // 5. 同 id 再推一次(更旧的时间戳):接受或 server_has_newer 都算协议正确,
        //    崩掉才算错
        val pushAgain = runBlocking {
            api.push(base, registered.token, listOf(row.copy(updatedAt = row.updatedAt - 1)))
        }
        val again = pushAgain.results.single()
        assertTrue("同 id 重复推应有明确结果,而不是崩掉", again.accepted || again.reason != null)

        // 6. 登出 → 这把钥匙立刻作废
        runBlocking { api.logout(base, registered.token) }
        val after = runCatching { runBlocking { api.me(base, registered.token) } }
            .exceptionOrNull() as? LnxApiException
        assertEquals("登出后 me 应 401", "unauthorized", after?.code ?: "no-exception")
    }
}
