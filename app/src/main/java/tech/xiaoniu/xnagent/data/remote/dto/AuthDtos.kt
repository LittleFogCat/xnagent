package tech.xiaoniu.xnagent.data.remote.dto

import kotlinx.serialization.Serializable

/** 注册验证码题目 */
@Serializable
data class RegisterCaptchaResponse(
    val challengeId: String,
    val question: String,
    val expiresInMs: Long,
)

/** 发送注册验证码请求 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val captchaId: String,
    val captchaAnswer: String,
)

/** 发送注册验证码响应 */
@Serializable
data class RegisterRequestResponse(
    val success: Boolean,
    val email: String,
    val expiresInMs: Long,
    val retryAfterSeconds: Long,
    val remainingThisHour: Long,
)

/** 完成注册请求 */
@Serializable
data class RegisterVerifyRequest(
    val email: String,
    val code: String,
)

/** 登录请求 */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/** 登录/注册用户对象 */
@Serializable
data class AuthUserDto(
    val username: String,
    val email: String,
)

/** 登录/注册响应（v1） */
@Serializable
data class AuthResponse(
    val success: Boolean,
    val token: String,
    val user: AuthUserDto,
)

// ---- v2 认证 DTO ----

/** v2 登录请求 */
@Serializable
data class LoginV2Request(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String? = null,
)

/** v2 登录响应 */
@Serializable
data class LoginV2Response(
    val success: Boolean,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUserDto,
)

/** 刷新 access token 请求 */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String,
)

/** 刷新 access token 响应 */
@Serializable
data class RefreshResponse(
    val success: Boolean,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: AuthUserDto,
)

/** v2 登出请求 */
@Serializable
data class LogoutV2Request(
    val refreshToken: String,
)

/** v2 登出响应 */
@Serializable
data class LogoutV2Response(
    val success: Boolean,
    val message: String,
)