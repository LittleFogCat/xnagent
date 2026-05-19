package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import tech.xiaoniu.xnagent.data.local.AuthStore
import tech.xiaoniu.xnagent.data.remote.api.AuthApi
import tech.xiaoniu.xnagent.data.remote.dto.LoginRequest
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequest
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequestResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterVerifyRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证仓库实现。
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val authStore: AuthStore,
) : AuthRepository {
    override val session: StateFlow<AuthSession> = authStore.session

    override suspend fun login(email: String, password: String): AuthSession {
        val response = authApi.login(
            LoginRequest(
                email = email.trim(),
                password = password,
            )
        )
        return response.toAuthSession().also(authStore::saveSession)
    }

    override suspend fun requestRegisterCaptcha(): RegisterCaptchaResponse {
        return authApi.getRegisterCaptcha()
    }

    override suspend fun requestRegister(
        email: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): RegisterRequestResponse {
        return authApi.requestRegister(
            RegisterRequest(
                email = email.trim(),
                password = password,
                captchaId = captchaId,
                captchaAnswer = captchaAnswer.trim(),
            )
        )
    }

    override suspend fun verifyRegister(email: String, code: String): AuthSession {
        val response = authApi.verifyRegister(
            RegisterVerifyRequest(
                email = email.trim(),
                code = code.trim(),
            )
        )
        return response.toAuthSession().also(authStore::saveSession)
    }

    override suspend fun continueAsGuest() {
        authStore.saveSession(AuthSession(isGuest = true))
    }

    override suspend fun logout() {
        authStore.clear()
    }
}