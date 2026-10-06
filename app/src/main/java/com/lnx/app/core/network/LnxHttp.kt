package com.lnx.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.security.cert.X509Certificate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 所有编解码共用的 Json 配置(服务端字段以后只会多不会少,缺的别让客户端崩) */
fun lnxJson(): Json = Json {
    ignoreUnknownKeys = true
}

/**
 * `GET /health` 的应答。字段以服务端 `ServerRoutes`/README 为准:
 * `{"status":"ok","service":"lnx-server","version":"0.1.0","serverTime":…,"schemaVersion":1,"database":"ok"}`
 *
 * [SerialName] 只在一个字段名和 Kotlin 名不一致时才需要 —— 这里恰好全一致,
 * 列出来是为了说明:改名会让老服务器的应答解析直接失败,别随手改。
 */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val serverTime: Long,
    val schemaVersion: Int,
    val database: String,
)

/**
 * 建一个**证书固定**的 Ktor HttpClient(v0.3 A2)。
 *
 * 与 [CertificatePinner] 的分工:
 * - 信任锚 = 内置的那张自签证书(逐字节比对),不是系统 CA;
 * - 主机名校验**有意关闭**(证书里没有主机名,见 PLAN-v0.3 §四 第 9 条);
 * - 引擎选 okhttp,它是 Android 上证书固定文档最全、坑最少的路线。
 *
 * 超时是刻意偏紧的:同步是"打开 App 顺手拉一次",网络不通时 15 秒内必须返回失败,
 * 不能让用户对着转圈的界面干等 —— 断网时本地日历照常可用。
 */
fun pinnedHttpClient(pinned: X509Certificate): HttpClient = HttpClient(OkHttp) {
    // 4xx/5xx 必须抛异常而不是把错误体当成功应答去解析 ——
    // Ktor 2 起这个开关默认是关的,不开的话错误映射(withApiError)永远接不到 ResponseException
    expectSuccess = true
    install(ContentNegotiation) { json(lnxJson()) }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
    }
    engine {
        config {
            val (factory, trust) = CertificatePinner.sslContext(pinned)
            sslSocketFactory(factory, trust)
            hostnameVerifier(CertificatePinner.anyHostname)
        }
    }
}
