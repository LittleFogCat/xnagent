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
    /** 认证状态直接代理给本地存储，供全局路由统一观察。 */
    override val session: StateFlow<AuthSession> = authStore.session

    override suspend fun login(email: String, password: String): AuthSession {
        // 登录成功后立即落地到本地存储，保证应用重启后仍能恢复会话。
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
        // 发送前统一 trim 输入，减少由前后空格造成的无效请求。
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
        // 注册校验成功后直接建立登录态，减少用户额外登录步骤。
        val response = authApi.verifyRegister(
            RegisterVerifyRequest(
                email = email.trim(),
                code = code.trim(),
            )
        )
        return response.toAuthSession().also(authStore::saveSession)
    }

    override suspend fun continueAsGuest() {
        // 游客模式只写入本地会话，不调用远端认证接口。
        authStore.saveSession(AuthSession(isGuest = true))
    }

    override suspend fun logout() {
        // 清空本地存储即可退出，后续路由会根据 session 自动跳回登录页。
        authStore.clear()
    }
}