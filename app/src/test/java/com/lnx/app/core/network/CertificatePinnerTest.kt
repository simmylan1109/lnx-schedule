package com.lnx.app.core.network

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 证书固定的规则锁(v0.3 A2)。
 *
 * 两张证书都放测试资源里:
 * - `server_cert.pem` = 真服务器那张(和 res/raw 里的是同一张,指纹断言双保险)
 * - `other_cert.pem` = 一次性生成的"别人家证书",只用来验"不是我们那张必须被拒"
 */
class CertificatePinnerTest {

    private val real: X509Certificate by lazy { load("/server_cert.pem") }
    private val other: X509Certificate by lazy { load("/other_cert.pem") }

    private fun load(resource: String): X509Certificate =
        CertificatePinner.loadPinnedCert(
            checkNotNull(javaClass.getResourceAsStream(resource)) { "missing test resource $resource" },
        )

    private fun sha256(cert: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(cert.encoded)
        .joinToString("") { "%02x".format(it) }

    @Test
    fun `内置证书指纹与已验收的服务器证书一致`() {
        // 2026-10-06 重签后的那张(CN=lnx,替掉了空 DN 的初版——空 DN 被 Java 拒收):
        // 从服务器下载并双算法校验过。断言指纹 = 换 res/raw 里的证书时必须先来这里改,
        // 防止无意识替身。
        assertEquals(
            "f4dfad31ec79d0a4e9266de8e2f9abea4057f22e11caebcd01925fa3e860fb6c",
            sha256(real),
        )
    }

    @Test
    fun `真证书通过固定校验`() {
        // 不抛异常即通过;认证类型对自签服务器无意义,传 RSA 即可
        CertificatePinner.trustManager(real).checkServerTrusted(arrayOf(real), "RSA")
    }

    @Test
    fun `别人的证书必须被拒`() {
        val trust = CertificatePinner.trustManager(real)
        val e = assertThrows(CertificateException::class.java) {
            trust.checkServerTrusted(arrayOf(other), "RSA")
        }
        assertTrue(e.message!!.contains("pins"))
    }

    @Test
    fun `只看叶子证书_链尾塞真证书也不放行`() {
        // 别人的证书当叶子、把我们的塞进链尾 —— 依然要拒:
        // 服务端身份 = 链上的第一张,拿我们的证书当"陪衬"骗不过去
        val trust = CertificatePinner.trustManager(real)
        assertThrows(CertificateException::class.java) {
            trust.checkServerTrusted(arrayOf(other, real), "RSA")
        }
    }

    @Test
    fun `真证书当叶子即使命名链里有杂项也放行`() {
        // 自签服务器只发自己一张;这里验的规则是"只比叶子,不被链里其他内容干扰"
        CertificatePinner.trustManager(real).checkServerTrusted(arrayOf(real, other), "RSA")
    }

    @Test
    fun `空链被拒`() {
        assertThrows(CertificateException::class.java) {
            CertificatePinner.trustManager(real).checkServerTrusted(arrayOf(), "RSA")
        }
    }

    @Test
    fun `主机名校验有意关闭_任意主机名都放行`() {
        // 证书里没有主机名(带了等于把服务器地址泄露进 APK),身份靠整张证书比对锁死。
        // 这条测试是"关掉是故意的"的证据:哪天有人顺手改成 return hostname == …
        // App 会立刻连不上真服务器(证书 subject 为空)。
        // **别往这里放真服务器地址** —— 地址绝不入库(2026-10-02 事故红线)。
        assertTrue(CertificatePinner.anyHostname.verify("totally-made-up.invalid", null))
        assertTrue(CertificatePinner.anyHostname.verify("999.999.999.999", null))
    }
}
