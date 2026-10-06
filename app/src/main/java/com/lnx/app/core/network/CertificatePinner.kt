package com.lnx.app.core.network

import java.io.InputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * 证书固定(v0.3 A2)。规则来自 PLAN-v0.3 §四 第 9 条,逐条对照:
 *
 * 1. **只认这一张证书**:服务端证书是自签的、故意不含主机名(带主机名等于把服务器地址
 *    二次泄露进 APK —— APK 发在 GitHub 上)。所以这里不走 CA 链校验,而是拿收到的叶子
 *    证书和内置的 [R.raw.server_cert] **逐字节比对 DER**。别人拿到任何其他有效证书都无效。
 * 2. **不校验主机名**:证书里没有名字可校,而且身份已经由上一条锁死 ——
 *    [AnyHostnameVerifier] 是**有意**的,不是偷懒。
 * 3. **pinning ≠ 端到端加密**:它防的是传输过程中的中间人,不防服务器本身。
 *    数据在服务器上是明文可读的(服务器是用户自己的机器)。写文档和隐私文案时
 *    必须守住这条措辞(docs/PRIVACY.md §5)。
 *
 * 首次信任/换证书/换机重装的敏感性是这个方案的代价:证书换了,所有旧 App 都连不上,
 * 必须发新版 —— 这是设计如此,不是 bug。
 */
object CertificatePinner {

    /** 从流里读出内置证书(资源在 `res/raw/server_cert.pem`) */
    fun loadPinnedCert(stream: InputStream): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(stream) as X509Certificate
    }

    /**
     * 只信任 [pinned] 这一张证书的 TrustManager。
     *
     * 比对的是**整张证书的 DER 编码**而不是指纹/公钥:证书一变(续期、重签、换服务器)
     * 就是另一张身份,App 必须连不上而不是"顺手接受"。
     * 链上只有叶子证书有意义 —— 自签服务器只发自己那张,链尾就算塞了别的也不放行。
     */
    fun trustManager(pinned: X509Certificate): X509TrustManager =
        object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull()
                    ?: throw CertificateException("server presented an empty certificate chain")
                val ok = pinned.encoded.contentEquals(leaf.encoded)
                if (!ok) {
                    throw CertificateException(
                        "server certificate is not the one lnx pins " +
                            "(expected sha256=" + sha256Hex(pinned.encoded).take(16) +
                            "…, got sha256=" + sha256Hex(leaf.encoded).take(16) + "…)",
                    )
                }
            }

                        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
                throw CertificateException("lnx never authenticates a client with a certificate")

            // 空数组比 arrayOf(pinned) 语义更准:这里不是"信任的 CA 列表",
            // 客户端认证路径(checkClientTrusted)永远抛异常,这数组不会被用到
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

    /** 一次性建好 SSL 上下文;OkHttp 需要 (factory, trustManager) 成对传入 */
    fun sslContext(pinned: X509Certificate): Pair<SSLSocketFactory, X509TrustManager> {
        val trust = trustManager(pinned)
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trust), null)
        return context.socketFactory to trust
    }

    /** 见类文档第 2 条:主机名校验被有意关闭 */
    val anyHostname: HostnameVerifier = HostnameVerifier { _, _ -> true }

    private fun sha256Hex(bytes: ByteArray): String = java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
