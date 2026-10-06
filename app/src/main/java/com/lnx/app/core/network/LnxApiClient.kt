package com.lnx.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把一次失败的请求翻译成 [LnxApiException](v0.3 A3)。
 * 纯函数、可直接单测:MockEngine 里抛异常会被引擎再包一层,
 * 那测出来的是 Ktor 的包装行为,不是我们的映射规则。
 * [parsedError] 是 ResponseException 分支在挂起上下文里解出来的错误体。
 */
fun apiExceptionFrom(e: Throwable, parsedError: ApiErrorBody?): LnxApiException = when (e) {
    is ResponseException -> LnxApiException(
        code = parsedError?.error?.code ?: "http_${e.response.status.value}",
        message = parsedError?.error?.message ?: (e.message ?: "request failed"),
        httpStatus = e.response.status.value,
        cause = e,
    )
    // 断网/地址错/DNS 失败/超时,全是 IOException 家族
    is IOException -> LnxApiException(code = "unreachable", message = e.message ?: "network unreachable", cause = e)
    else -> LnxApiException(code = "unreachable", message = e.message ?: "unexpected error", cause = e)
}

/**
 * lnx 同步服务器的 API 客户端(v0.3 A2 传输 + A3 认证)。
 *
 * **服务器地址为什么是方法参数而不是内置常量**:地址是用户自己配的(无域名、走 IP,
 * 换服务器/换 IP 都要能改 —— PLAN-v0.3 §四 A3),而且**公网地址绝不入仓库**
 * (2026-10-02 那次事故就是地址进了公开仓库)。它只存在两处:
 * 用户在设置里填的值,和本机 gitignored 的 `local.properties`(测试用)。
 *
 * 错误统一走 [withApiError]:服务端的 `{error:{code,message}}` 被翻成 [LnxApiException],
 * 断网/地址错翻成 code=unreachable —— 界面只需要认一种异常。
 */
@Singleton
class LnxApiClient @Inject constructor(
    val http: HttpClient,
) : SyncApi {

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

    /** 注册并直接拿到令牌(服务端注册即登录,少一次来回) */
    suspend fun register(baseUrl: String, username: String, password: String, inviteCode: String): AuthResponse =
        withApiError {
            http.post("${baseUrl.trimEnd('/')}/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username, password, inviteCode))
            }.body()
        }

    suspend fun login(baseUrl: String, username: String, password: String): AuthResponse =
        withApiError {
            http.post("${baseUrl.trimEnd('/')}/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }.body()
        }

    /** "我是谁":App 启动时拿本机存的令牌验一下还能不能用(A4 用,A3 先铺好) */
    suspend fun me(baseUrl: String, token: String): MeResponse =
        withApiError {
            http.get("${baseUrl.trimEnd('/')}/v1/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        }

    /** 服务端返回 204;令牌在服务端被吊销,本地的由调用方清 */
    suspend fun logout(baseUrl: String, token: String) {
        withApiError {
            http.post("${baseUrl.trimEnd('/')}/v1/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    /** 拉取 since 之后变过的行(A4)。since 传 0 = 从头拉 */
    override suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int): PullResponse =
        withApiError {
            http.get("${baseUrl.trimEnd('/')}/v1/sync/pull") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("since", since)
                parameter("limit", limit)
            }.body()
        }

    /** 推送本地行(A4),服务端逐行 LWW 并给结果;分批是引擎的事,这里只管一发 */
    override suspend fun push(baseUrl: String, token: String, rows: List<SyncRowDto>): PushResponse =
        withApiError {
            http.post("${baseUrl.trimEnd('/')}/v1/sync/push") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PushRequest(rows))
            }.body()
        }

    /**
     * 把 Ktor 的异常统一翻译成 [LnxApiException]:
     * - 4xx/5xx → 解响应体里的 `{error:{code,message}}`;解析不出就退回 HTTP 状态码
     * - 断网/地址错/超时(IOException 家族)→ code=unreachable,给界面一个可提示的状态
     * 其余异常也归到 unreachable —— 它们都意味着"没有拿到服务端的任何答复"。
     * (expectSuccess = true 是前提,见 [pinnedHttpClient]。)
     */
    private suspend fun <T> withApiError(block: suspend () -> T): T =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 协程要退出就退出,别翻译成网络错误
        } catch (e: LnxApiException) {
            throw e
        } catch (e: ResponseException) {
            // 错误体要在挂起上下文里解;解不出来(比如 500 的 HTML)按 null 走状态码兜底
            val parsed = runCatching { e.response.body<ApiErrorBody>() }.getOrNull()
            throw apiExceptionFrom(e, parsed)
        } catch (e: Throwable) {
            throw apiExceptionFrom(e, null)
        }
}

/**
 * 同步引擎只依赖这个窄口(A4):引擎测试用假件,不必碰真网络/证书。
 */
interface SyncApi {
    suspend fun pull(baseUrl: String, token: String, since: Long, limit: Int = 500): PullResponse
    suspend fun push(baseUrl: String, token: String, rows: List<SyncRowDto>): PushResponse
}
