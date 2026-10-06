package com.lnx.app.core.network

import io.ktor.client.request.get
import io.ktor.client.call.body
import java.io.File
import java.security.cert.X509Certificate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **真服务器端到端**:用内置的那张固定证书,对真服务器握手并拿到 /health。
 *
 * 这条测试存在的意义是证明三件事凑在一起真的能工作:
 * 整张证书比对放行 + 主机名校验关闭(证书无名字)+ Ktor/OkHttp 的装配没有把这两条弄丢。
 * 任何一层错(证书换错、engine 配置没生效、证书固定被劫持者替换)这里都会红。
 *
 * **服务器地址不入库**:只从本机 gitignored 的 `local.properties`(`lnx.test.server`)
 * 或环境变量 `LNX_TEST_SERVER` 读。两处都没有时整条跳过 —— CI 和别人的机器上
 * 它就该安静跳过,绝不把地址硬编码进来(2026-10-02 事故红线)。
 */
class PinnedHealthCheckTest {

    private fun testServerUrl(): String? {
        System.getenv("LNX_TEST_SERVER")?.let { return it }
        System.getProperty("lnx.test.server")?.let { return it }
        for (file in listOf(File("local.properties"), File("../local.properties"))) {
            if (!file.isFile) continue
            file.readLines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("lnx.test.server=") }
                ?.substringAfter("=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun pinnedCert(resource: String): X509Certificate =
        CertificatePinner.loadPinnedCert(
            checkNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" },
        )

    @Test
    fun `固定真证书能连上真服务器并拿到健康状态`() {
        val url = testServerUrl()
        assumeTrue("lnx.test.server not configured on this machine — skip", url != null)

        val client = pinnedHttpClient(pinnedCert("/server_cert.pem"))
        try {
            runBlocking {
                val health = LnxApiClient(client).health(url!!)
                assertEquals("ok", health.status)
                assertEquals("lnx-server", health.service)
                assertEquals("ok", health.database)
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `固定了错误证书时_真服务器必须连不上`() {
        val url = testServerUrl()
        assumeTrue("lnx.test.server not configured on this machine — skip", url != null)

        val client = pinnedHttpClient(pinnedCert("/other_cert.pem"))
        try {
            runBlocking {
                client.get("$url/health").body<HealthResponse>()
            }
            throw AssertionError("wrong certificate pinned but the server was reachable — pinning is not enforced")
        } catch (expected: AssertionError) {
            throw expected
        } catch (t: Throwable) {
            // 连不上还不够 —— 必须是**因为固定校验**连不上。
            // 服务器挂了/断网了也会连不上,那不算证据。
            // ⚠️ 异常消息里的 "pins" 是本测试的契约:改 CertificatePinner 的报错文案
            //    时必须同步改这里,否则这条测试会假红。
            val chain = generateSequence(t) { it.cause }.map { it.message.orEmpty() }
            if (chain.none { it.contains("pins") }) {
                throw AssertionError("failed for a non-pinning reason: $t", t)
            }
        } finally {
            client.close()
        }
    }
}
