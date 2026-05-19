package tech.xiaoniu.xnagent.ui.model

data class LoginUiState(
    val isRegisterMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val captchaId: String = "",
    val captchaQuestion: String = "",
    val captchaAnswer: String = "",
    val verificationCode: String = "",
    val codeRequested: Boolean = false,
    val isSubmitting: Boolean = false,
    val noticeMessage: String? = null,
    val errorMessage: String? = null,
)