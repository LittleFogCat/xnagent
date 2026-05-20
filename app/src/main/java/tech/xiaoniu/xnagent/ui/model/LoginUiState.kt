package tech.xiaoniu.xnagent.ui.model

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
    val codeRequested: Boolean = false,
    val isSubmitting: Boolean = false,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
)