package com.lnx.app.core.network

import kotlinx.serialization.Serializable

/**
 * 认证协议的 DTO 与错误(v0.3 A3)。
 *
 * **字段名以服务端 `http/AuthRoutes.kt` 为准,逐字对齐**——协议两边靠约定不靠同版本
 * (服务端 Kotlin 2.2 / 客户端 2.0),改任何一边的名字都是断协议。
 * 服务端"以后只会多不会少"的字段由 [lnxJson] 的 ignoreUnknownKeys 兜住。
 */

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val inviteCode: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
)

/** 注册/登录成功时的一次性应答。token 只在这一刻存在客户端内存里,随即进 [TokenStore] */
@Serializable
data class AuthResponse(
    val token: String,
    val expiresAt: Long,
    val user: UserDto,
)

@Serializable
data class MeResponse(
    val user: UserDto,
)

/** 服务端所有非 2xx 的统一错误形状:`{"error":{"code":"…","message":"…"}}` */
@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail,
)

@Serializable
data class ApiErrorDetail(
    val code: String,
    val message: String,
)

/**
 * 客户端侧的 API 错误。
 *
 * [code] 是服务端的稳定标识(invite_required / username_taken / locked_out /
 * unauthorized / registration_closed / weak_password / bad_username…),
 * 界面按它分支、映射成本地语言的文案;**message 是服务端给人看的、可能随版本改写**,
 * 只做兜底展示,不要拿它做判断。
 *
 * 客户端自己也有一个本地 code:
 * - `unreachable` —— 连不上(断网/地址错/DNS 解析失败),跟服务端 4xx/5xx 区分开
 */
class LnxApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
