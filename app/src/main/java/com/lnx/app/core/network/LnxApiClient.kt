package com.lnx.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Singleton

/**
 * lnx 同步服务器的 API 客户端(v0.3 A2)。
 *
 * A2 只铺传输:固定证书的 [HttpClient] + `/health`。注册/登录(A3)、
 * pull/push(A4)在同一层往上加,认证头从 [TokenStore] 取。
 *
 * **服务器地址为什么是方法参数而不是内置常量**:地址是用户自己配的(无域名、走 IP,
 * 换服务器/换 IP 都要能改 —— PLAN-v0.3 §四 A3),而且**公网地址绝不入仓库**
 * (2026-10-02 那次事故就是地址进了公开仓库)。它只存在两处:
 * 用户在设置里填的值,和本机 gitignored 的 `local.properties`(测试用)。
 */
@Singleton
class LnxApiClient @Inject constructor(
    val http: HttpClient,
) {

    /**
     * 健康检查。**https 是硬要求**:内置的固定证书只对 TLS 有效,
     * 明文 http 既不符合部署架构(公网只有 nginx 的 8443),也不该被允许降级。
     */
    suspend fun health(baseUrl: String): HealthResponse {
        require(baseUrl.startsWith("https://")) {
            "server address must start with https:// (pinned certificate only exists for TLS)"
        }
        return http.get("${baseUrl.trimEnd('/')}/health").body()
    }
}
