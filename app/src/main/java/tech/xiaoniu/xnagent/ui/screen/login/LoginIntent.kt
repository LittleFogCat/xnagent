package tech.xiaoniu.xnagent.ui.screen.login

/**
 * 登录/注册页面意图集合。
 */
sealed class LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent()

    data class UpdatePassword(val password: String) : LoginIntent()

    data class UpdateCaptchaAnswer(val answer: String) : LoginIntent()

    data class UpdateVerificationCode(val code: String) : LoginIntent()

    object ToggleMode : LoginIntent()

    object RefreshCaptcha : LoginIntent()

    object Login : LoginIntent()

    object RequestRegisterCode : LoginIntent()

    object CompleteRegister : LoginIntent()

    object ContinueAsGuest : LoginIntent()
}