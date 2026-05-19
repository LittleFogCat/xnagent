package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import tech.xiaoniu.xnagent.data.remote.dto.AuthResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequestResponse

/**
 * 认证相关仓库。
 */
interface AuthRepository {
    val session: StateFlow<AuthSession>

    suspend fun login(email: String, password: String): AuthSession

    suspend fun requestRegisterCaptcha(): RegisterCaptchaResponse

    suspend fun requestRegister(
        email: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): RegisterRequestResponse

    suspend fun verifyRegister(email: String, code: String): AuthSession

    suspend fun continueAsGuest()

    suspend fun logout()
}

internal fun AuthResponse.toAuthSession(): AuthSession {
    return AuthSession(
        token = token,
        user = AuthUser(
            username = user.username,
            email = user.email,
        ),
        isGuest = false,
    )
}