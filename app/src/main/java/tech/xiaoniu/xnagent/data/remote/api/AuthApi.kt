package tech.xiaoniu.xnagent.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import tech.xiaoniu.xnagent.data.remote.dto.AuthResponse
import tech.xiaoniu.xnagent.data.remote.dto.LoginRequest
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequest
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequestResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterVerifyRequest

/**
 * 认证相关接口。
 */
interface AuthApi {
    @GET("/api/register/captcha")
    suspend fun getRegisterCaptcha(): RegisterCaptchaResponse

    @POST("/api/register/request")
    suspend fun requestRegister(@Body request: RegisterRequest): RegisterRequestResponse

    @POST("/api/register/verify")
    suspend fun verifyRegister(@Body request: RegisterVerifyRequest): AuthResponse

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
}