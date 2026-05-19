package tech.xiaoniu.xnagent.ui.screen.login

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
import javax.inject.Inject

/**
 * 登录/注册页状态。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

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
            it.copy(email = value, errorMessage = null, noticeMessage = null)
        }
    }

    fun updatePassword(value: String) {
        _uiState.update {
            it.copy(password = value, errorMessage = null, noticeMessage = null)
        }
    }

    fun updateCaptchaAnswer(value: String) {
        _uiState.update {
            it.copy(captchaAnswer = value, errorMessage = null, noticeMessage = null)
        }
    }

    fun updateVerificationCode(value: String) {
        _uiState.update {
            it.copy(verificationCode = value, errorMessage = null, noticeMessage = null)
        }
    }

    fun toggleMode() {
        val nextIsRegisterMode = !_uiState.value.isRegisterMode
        _uiState.update {
            it.copy(
                isRegisterMode = nextIsRegisterMode,
                captchaAnswer = "",
                verificationCode = "",
                codeRequested = false,
                noticeMessage = null,
                errorMessage = null,
            )
        }
        if (nextIsRegisterMode) {
            refreshCaptcha()
        }
    }

    fun refreshCaptcha() {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, noticeMessage = null) }
            runCatching {
                authRepository.requestRegisterCaptcha()
            }.onSuccess { captcha ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        captchaId = captcha.challengeId,
                        captchaQuestion = captcha.question,
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

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入邮箱和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, noticeMessage = null) }
            runCatching {
                authRepository.login(state.email, state.password)
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

    fun requestRegisterCode() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入邮箱和密码") }
            return
        }
        if (state.captchaId.isBlank() || state.captchaAnswer.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请完成人机验证") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, noticeMessage = null) }
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
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.toDisplayMessage(),
                    )
                }
            }
        }
    }

    fun completeRegister() {
        val state = _uiState.value
        if (state.email.isBlank() || state.verificationCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入邮箱和验证码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, noticeMessage = null) }
            runCatching {
                authRepository.verifyRegister(state.email, state.verificationCode)
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
}