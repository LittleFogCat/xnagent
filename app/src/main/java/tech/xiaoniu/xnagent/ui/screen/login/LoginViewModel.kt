package tech.xiaoniu.xnagent.ui.screen.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.ui.model.LoginUiState
import java.io.IOException
import kotlin.random.Random
import javax.inject.Inject

/**
 * 登录/注册页状态管理。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())

    /** 登录/注册页唯一状态源，包含表单输入、校验错误和提交状态。 */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    companion object {
        private const val LOGIN_CAPTCHA_TRIGGER_COUNT = 3
        private const val LOGIN_CAPTCHA_LENGTH = 5
    }

    /** 统一处理页面上的输入事件、模式切换与提交动作。 */
    fun dispatch(intent: LoginIntent) {
        when(intent) {
            is LoginIntent.UpdateEmail -> updateEmail(intent.email)
            is LoginIntent.UpdatePassword -> updatePassword(intent.password)
            is LoginIntent.UpdateCaptchaAnswer -> updateCaptchaAnswer(intent.answer)
            is LoginIntent.UpdateVerificationCode -> updateVerificationCode(intent.code)
            is LoginIntent.ToggleMode -> toggleMode()
            is LoginIntent.RefreshCaptcha -> refreshCaptcha()
            is LoginIntent.Login -> login()
            is LoginIntent.RequestRegisterCode -> requestRegisterCode()
            is LoginIntent.CompleteRegister -> completeRegister()
            is LoginIntent.ContinueAsGuest -> continueAsGuest()
        }
    }

    fun updateEmail(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    fun updatePassword(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    fun updateCaptchaAnswer(value: String) {
        _uiState.update {
            if (it.isRegisterMode) {
                it.copy(
                    captchaAnswer = value,
                    captchaAnswerError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            } else {
                it.copy(
                    loginCaptchaAnswer = value,
                    loginCaptchaAnswerError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
        }
    }

    fun updateVerificationCode(value: String) {
        _uiState.update {
            it.copy(
                verificationCode = value,
                verificationCodeError = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    /** 切换登录/注册模式，并准备目标模式所需的验证码状态。 */
    fun toggleMode() {
        val nextIsRegisterMode = !_uiState.value.isRegisterMode
        _uiState.update {
            it.copy(
                isRegisterMode = nextIsRegisterMode,
                captchaAnswer = "",
                verificationCode = "",
                codeRequested = false,
                emailError = null,
                passwordError = null,
                captchaAnswerError = null,
                verificationCodeError = null,
                noticeMessage = null,
                errorMessage = null,
            )
        }
        // 注册模式需要远端题目；登录模式只在触发保护后才展示本地图形验证码。
        if (nextIsRegisterMode) {
            refreshRegisterCaptcha()
        } else if (_uiState.value.loginCaptchaRequired && _uiState.value.loginCaptchaValue.isBlank()) {
            refreshLoginCaptcha()
        }
    }

    /** 根据当前模式刷新对应的人机验证题目。 */
    fun refreshCaptcha() {
        if (_uiState.value.isRegisterMode) {
            refreshRegisterCaptcha()
        } else {
            refreshLoginCaptcha()
        }
    }

    /** 向服务端请求注册题目，并重置注册阶段的人机验证输入。 */
    private fun refreshRegisterCaptcha() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    captchaAnswerError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            runCatching {
                authRepository.requestRegisterCaptcha()
            }.onSuccess { captcha ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        captchaId = captcha.challengeId,
                        captchaQuestion = captcha.question,
                        captchaAnswer = "",
                        captchaAnswerError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.toDisplayMessage(),
                    )
                }
            }
        }
    }

    /** 生成登录失败保护使用的本地图形验证码。 */
    private fun refreshLoginCaptcha() {
        if (_uiState.value.isSubmitting || !_uiState.value.loginCaptchaRequired) return
        _uiState.update {
            it.copy(
                loginCaptchaValue = createLoginCaptchaValue(),
                loginCaptchaAnswer = "",
                loginCaptchaAnswerError = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    /** 执行登录流程，并在连续失败时逐步升级为带图形验证码的保护模式。 */
    fun login() {
        val state = _uiState.value
        // 先同步执行本地校验，减少无效网络请求并及时反馈具体字段错误。
        val emailError = validateEmail(state.email)
        val passwordError = validatePassword(state.password, requireStrongPassword = false)
        val loginCaptchaValidation = if (state.loginCaptchaRequired) {
            validateLoginCaptcha(
                expectedValue = state.loginCaptchaValue,
                answer = state.loginCaptchaAnswer,
            )
        } else {
            CaptchaValidationResult()
        }
        if (emailError != null || passwordError != null || loginCaptchaValidation.errorMessage != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    loginCaptchaValue = if (loginCaptchaValidation.shouldRefreshCaptcha) {
                        createLoginCaptchaValue()
                    } else {
                        it.loginCaptchaValue
                    },
                    loginCaptchaAnswer = if (loginCaptchaValidation.shouldClearAnswer) {
                        ""
                    } else {
                        it.loginCaptchaAnswer
                    },
                    loginCaptchaAnswerError = loginCaptchaValidation.errorMessage,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    passwordError = null,
                    loginCaptchaAnswerError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            runCatching {
                authRepository.login(state.email, state.password)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        loginFailedAttempts = 0,
                        loginCaptchaRequired = false,
                        loginCaptchaValue = "",
                        loginCaptchaAnswer = "",
                        loginCaptchaAnswerError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    when {
                        error is HttpException && error.code() == 401 -> {
                            // 连续失败达到阈值后开启图形验证码，降低撞库和暴力尝试风险。
                            val nextFailedAttempts = it.loginFailedAttempts + 1
                            val shouldRequireCaptcha = nextFailedAttempts >= LOGIN_CAPTCHA_TRIGGER_COUNT
                            it.copy(
                                isSubmitting = false,
                                loginFailedAttempts = nextFailedAttempts,
                                loginCaptchaRequired = shouldRequireCaptcha,
                                loginCaptchaValue = if (shouldRequireCaptcha) {
                                    createLoginCaptchaValue()
                                } else {
                                    it.loginCaptchaValue
                                },
                                loginCaptchaAnswer = if (shouldRequireCaptcha) "" else it.loginCaptchaAnswer,
                                loginCaptchaAnswerError = null,
                                emailError = "邮箱或密码错误",
                                passwordError = "邮箱或密码错误",
                                noticeMessage = if (nextFailedAttempts == LOGIN_CAPTCHA_TRIGGER_COUNT) {
                                    "已连续输错 3 次，请完成人机验证后继续登录"
                                } else {
                                    null
                                },
                            )
                        }

                        error is HttpException && error.code() == 400 -> {
                            it.copy(
                                isSubmitting = false,
                                emailError = "请检查邮箱格式",
                                passwordError = "请检查密码格式",
                                noticeMessage = null,
                            )
                        }

                        else -> {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = error.toDisplayMessage(),
                                noticeMessage = null,
                            )
                        }
                    }
                }
            }
        }
    }

    /** 提交注册第一步，校验邮箱/密码/题目答案并发送邮箱验证码。 */
    fun requestRegisterCode() {
        val state = _uiState.value
        val emailError = validateEmail(state.email)
        val passwordError = validatePassword(state.password, requireStrongPassword = true)
        if (state.captchaId.isBlank() || state.captchaQuestion.isBlank()) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    captchaAnswerError = null,
                    errorMessage = "人机验证加载失败，请刷新题目",
                    noticeMessage = null,
                )
            }
            return
        }
        val captchaAnswerError = validateCaptchaAnswer(state.captchaAnswer)
        if (emailError != null || passwordError != null || captchaAnswerError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    captchaAnswerError = captchaAnswerError,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    passwordError = null,
                    captchaAnswerError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            runCatching {
                authRepository.requestRegister(
                    email = state.email,
                    password = state.password,
                    captchaId = state.captchaId,
                    captchaAnswer = state.captchaAnswer,
                )
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        codeRequested = true,
                        noticeMessage = "验证码已发送到 ${response.email}，请在 10 分钟内完成注册",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    when {
                        error is HttpException && error.code() == 409 -> {
                            it.copy(
                                isSubmitting = false,
                                emailError = "该邮箱已被注册",
                            )
                        }

                        error is HttpException && error.code() == 400 -> {
                            it.copy(
                                isSubmitting = false,
                                captchaAnswerError = "人机验证错误或已过期，请刷新后重试",
                            )
                        }

                        else -> {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = error.toDisplayMessage(),
                            )
                        }
                    }
                }
            }
        }
    }

    /** 提交注册第二步，校验邮箱验证码并在成功后建立登录态。 */
    fun completeRegister() {
        val state = _uiState.value
        // 完成注册阶段只验证邮箱与验证码，密码强度已在上一步校验通过。
        val emailError = validateEmail(state.email)
        val verificationCodeError = validateVerificationCode(state.verificationCode)
        if (emailError != null || verificationCodeError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    verificationCodeError = verificationCodeError,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    emailError = null,
                    verificationCodeError = null,
                    errorMessage = null,
                    noticeMessage = null,
                )
            }
            runCatching {
                authRepository.verifyRegister(state.email, state.verificationCode)
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false) }
            }.onFailure { error ->
                _uiState.update {
                    when {
                        error is HttpException && error.code() == 400 -> {
                            it.copy(
                                isSubmitting = false,
                                verificationCodeError = "验证码错误或已过期",
                            )
                        }

                        error is HttpException && error.code() == 409 -> {
                            it.copy(
                                isSubmitting = false,
                                emailError = "该邮箱已被注册",
                            )
                        }

                        else -> {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = error.toDisplayMessage(),
                            )
                        }
                    }
                }
            }
        }
    }

    /** 以游客身份进入首页，跳过账号体系但保留统一的提交流程。 */
    fun continueAsGuest() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, noticeMessage = null) }
            runCatching {
                authRepository.continueAsGuest()
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.toDisplayMessage(),
                    )
                }
            }
        }
    }

    private fun Throwable.toDisplayMessage(): String {
        return when (this) {
            is HttpException -> when (code()) {
                400 -> "请求参数有误，请检查输入内容"
                401 -> "邮箱或密码错误"
                409 -> "该邮箱已被注册"
                429 -> "请求过于频繁，请稍后再试"
                else -> message()
            }

            is IOException -> "网络连接失败，请稍后重试"
            else -> message ?: "请求失败，请稍后重试"
        }
    }

    private fun validateEmail(value: String): String? {
        return when {
            value.isBlank() -> "请输入邮箱"
            !Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches() -> "邮箱格式不正确"
            else -> null
        }
    }

    private fun validatePassword(
        value: String,
        requireStrongPassword: Boolean,
    ): String? {
        return when {
            value.isBlank() -> "请输入密码"
            requireStrongPassword && value.length < 8 -> "密码至少 8 位"
            value.length > 128 -> "密码不能超过 128 位"
            else -> null
        }
    }

    private fun validateCaptchaAnswer(value: String): String? {
        return if (value.isBlank()) "请输入人机验证答案" else null
    }

    private fun validateVerificationCode(value: String): String? {
        return if (value.isBlank()) "请输入邮箱验证码" else null
    }

    private fun validateLoginCaptcha(
        expectedValue: String,
        answer: String,
    ): CaptchaValidationResult {
        // 登录验证码错误时同时要求刷新题目和清空输入，避免旧题继续提交。
        return when {
            expectedValue.isBlank() -> CaptchaValidationResult(
                errorMessage = "验证码已失效，请刷新后重试",
                shouldRefreshCaptcha = true,
                shouldClearAnswer = true,
            )

            answer.isBlank() -> CaptchaValidationResult(errorMessage = "请输入图形验证码")

            !expectedValue.equals(answer.trim(), ignoreCase = true) -> CaptchaValidationResult(
                errorMessage = "图形验证码不正确，请重试",
                shouldRefreshCaptcha = true,
                shouldClearAnswer = true,
            )

            else -> CaptchaValidationResult()
        }
    }

    private fun createLoginCaptchaValue(): String {
        val charset = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        return buildString(LOGIN_CAPTCHA_LENGTH) {
            repeat(LOGIN_CAPTCHA_LENGTH) {
                append(charset[Random.nextInt(charset.length)])
            }
        }
    }

    private data class CaptchaValidationResult(
        val errorMessage: String? = null,
        val shouldRefreshCaptcha: Boolean = false,
        val shouldClearAnswer: Boolean = false,
    )
}