package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import tech.xiaoniu.xnagent.data.local.AuthStore
import tech.xiaoniu.xnagent.data.local.TokenRefreshHandler
import tech.xiaoniu.xnagent.data.remote.api.AuthApi
import tech.xiaoniu.xnagent.data.remote.dto.LoginV2Request
import tech.xiaoniu.xnagent.data.remote.dto.LogoutV2Request
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequest
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
    private val tokenRefreshHandler: TokenRefreshHandler,
) : AuthRepository {
    /** 认证状态直接代理给本地存储，供全局路由统一观察。 */
    override val session: StateFlow<AuthSession> = authStore.session

    /** 注册流程中临时缓存的密码，用于注册完成后立即发起 v2 登录获取双 token。 */
    private var pendingRegisterPassword: String? = null

    override fun getDeviceId(): String = authStore.getDeviceId()

    override suspend fun login(
        email: String,
        password: String,
        deviceId: String,
        deviceName: String?,
    ): AuthSession {
        val response = authApi.loginV2(
            LoginV2Request(
                email = email.trim(),
                password = password,
                deviceId = deviceId,
                deviceName = deviceName,
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
        // 注册完成后需要发起 v2 登录以获取双 token，此处缓存密码供后续使用。
        pendingRegisterPassword = password
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
        val password = pendingRegisterPassword
        pendingRegisterPassword = null

        // 先完成注册，建立 v1 登录态。
        val verifyResponse = authApi.verifyRegister(
            RegisterVerifyRequest(
                email = email.trim(),
                code = code.trim(),
            )
        )

        // 注册成功后立即尝试 v2 登录，获取双 token 以实现完整的 Token Rotation 体验。
        if (password != null) {
            runCatching {
                val v2Response = authApi.loginV2(
                    LoginV2Request(
                        email = email.trim(),
                        password = password,
                        deviceId = authStore.getDeviceId(),
                    )
                )
                return v2Response.toAuthSession().also(authStore::saveSession)
            }
        }

        // 降级：无法获取 v2 token（如密码缓存丢失），使用 v1 格式 session，
        // 无 refreshToken，accessToken 过期后需重新登录。
        return AuthSession(
            token = verifyResponse.token,
            user = AuthUser(
                username = verifyResponse.user.username,
                email = verifyResponse.user.email,
            ),
            isGuest = false,
        ).also(authStore::saveSession)
    }

    override suspend fun continueAsGuest() {
        authStore.saveSession(AuthSession(isGuest = true))
    }

    override suspend fun logout() {
        // 尝试服务端吊销 refresh token，使当前设备登录态立即失效。
        val currentRefreshToken = authStore.session.value.refreshToken
        if (!currentRefreshToken.isNullOrBlank()) {
            runCatching {
                authApi.logoutV2(LogoutV2Request(refreshToken = currentRefreshToken))
            }
        }
        // 唤醒等待中的刷新线程，避免它们在登出后继续操作。
        tokenRefreshHandler.reset()
        authStore.clear()
    }
}
