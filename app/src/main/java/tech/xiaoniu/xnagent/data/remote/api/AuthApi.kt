package tech.xiaoniu.xnagent.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import tech.xiaoniu.xnagent.data.remote.dto.AuthResponse
import tech.xiaoniu.xnagent.data.remote.dto.LoginRequest
import tech.xiaoniu.xnagent.data.remote.dto.LoginV2Request
import tech.xiaoniu.xnagent.data.remote.dto.LoginV2Response
import tech.xiaoniu.xnagent.data.remote.dto.LogoutV2Request
import tech.xiaoniu.xnagent.data.remote.dto.LogoutV2Response
import tech.xiaoniu.xnagent.data.remote.dto.RefreshRequest
import tech.xiaoniu.xnagent.data.remote.dto.RefreshResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequest
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequestResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterVerifyRequest

/**
 * 认证相关接口。
 */
interface AuthApi {
    /** 获取注册流程所需的人机验证题目。 */
    @GET("/api/register/captcha")
    suspend fun getRegisterCaptcha(): RegisterCaptchaResponse

    /**
     * 提交注册申请并触发发送邮箱验证码。
     *
     * @param request 包含邮箱、密码和人机验证答案。
     */
    @POST("/api/register/request")
    suspend fun requestRegister(@Body request: RegisterRequest): RegisterRequestResponse

    /**
     * 校验邮箱验证码并返回登录态。
     *
     * @param request 包含注册邮箱和验证码。
     */
    @POST("/api/register/verify")
    suspend fun verifyRegister(@Body request: RegisterVerifyRequest): AuthResponse

    /**
     * 使用邮箱密码登录。
     *
     * @param request 登录请求体。
     */
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    /** v2 双 token 登录，需携带设备标识。 */
    @POST("/api/login-v2")
    suspend fun loginV2(@Body request: LoginV2Request): LoginV2Response

    /** 使用 refresh token 换取新的 access token（Token Rotation）。 */
    @POST("/api/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    /** 吊销 refresh token，使当前设备登录态立即失效。 */
    @POST("/api/logout-v2")
    suspend fun logoutV2(@Body request: LogoutV2Request): LogoutV2Response
}