package tech.xiaoniu.xnagent.ui.model

/**
 * 登录/注册页面状态。
 *
 * 同时覆盖登录、注册、图形验证码、人机验证和邮箱验证码流程。
 */
data class LoginUiState(
    val isRegisterMode: Boolean = false,
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val loginFailedAttempts: Int = 0,
    val loginCaptchaRequired: Boolean = false,
    val loginCaptchaValue: String = "",
    val loginCaptchaAnswer: String = "",
    val loginCaptchaAnswerError: String? = null,
    val captchaId: String = "",
    val captchaQuestion: String = "",
    val captchaAnswer: String = "",
    val captchaAnswerError: String? = null,
    val verificationCode: String = "",
    val verificationCodeError: String? = null,
    /** 是否已请求发送邮箱验证码。 */
    val codeRequested: Boolean = false,
    val isSubmitting: Boolean = false,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
)