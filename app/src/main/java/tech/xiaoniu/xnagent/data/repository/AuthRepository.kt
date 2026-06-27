package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import tech.xiaoniu.xnagent.data.remote.dto.AuthResponse
import tech.xiaoniu.xnagent.data.remote.dto.LoginV2Response
import tech.xiaoniu.xnagent.data.remote.dto.RefreshResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterCaptchaResponse
import tech.xiaoniu.xnagent.data.remote.dto.RegisterRequestResponse

/**
 * 认证相关仓库。
 */
interface AuthRepository {
    /** 当前认证态，供页面路由和权限判断统一监听。 */
    val session: StateFlow<AuthSession>

    /**
     * 使用邮箱密码登录（v2 双 token）。
     *
     * @param email 用户邮箱。
     * @param password 明文密码。
     * @param deviceId 设备唯一标识。
     * @param deviceName 设备名称（可选）。
     */
    suspend fun login(
        email: String,
        password: String,
        deviceId: String,
        deviceName: String? = null,
    ): AuthSession

    /** 获取持久化的设备唯一标识，用于 v2 登录和设备绑定。 */
    fun getDeviceId(): String

    /** 请求注册流程的人机验证题目。 */
    suspend fun requestRegisterCaptcha(): RegisterCaptchaResponse

    /**
     * 提交注册申请并发送邮箱验证码。
     *
     * @param email 注册邮箱。
     * @param password 注册密码。
     * @param captchaId 服务端返回的人机验证题目 ID。
     * @param captchaAnswer 用户输入的人机验证答案。
     */
    suspend fun requestRegister(
        email: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): RegisterRequestResponse

    /**
     * 使用邮箱验证码完成注册并建立登录态。
     *
     * @param email 注册邮箱。
     * @param code 邮箱验证码。
     */
    suspend fun verifyRegister(email: String, code: String): AuthSession

    /** 以游客身份进入首页。 */
    suspend fun continueAsGuest()

    /** 清理本地登录态并退出当前账号。 */
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

internal fun LoginV2Response.toAuthSession(): AuthSession {
    return AuthSession(
        token = accessToken,
        refreshToken = refreshToken,
        user = AuthUser(
            username = user.username,
            email = user.email,
        ),
        isGuest = false,
    )
}

internal fun RefreshResponse.toAuthSession(): AuthSession {
    return AuthSession(
        token = accessToken,
        refreshToken = refreshToken,
        user = AuthUser(
            username = user.username,
            email = user.email,
        ),
        isGuest = false,
    )
}