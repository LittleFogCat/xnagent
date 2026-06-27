package tech.xiaoniu.xnagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.data.repository.AuthSession
import javax.inject.Inject

sealed interface MainDestination {
    data object Home : MainDestination
    data object Settings : MainDestination
    data object Login : MainDestination
}

data class MainUiState(
    val session: AuthSession = AuthSession(),
    val destination: MainDestination = MainDestination.Login,
    /**
     * 待选中的会话 ID。
     *
     * 设为非空时，主页在初始化时需要选中该会话；消费后清空，避免重复触发。
     */
    val pendingSessionId: String? = null,
)

/**
 * 应用根状态管理。
 *
 * 负责根据认证态在首页、设置页和登录页之间切换路由。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            session = authRepository.session.value,
            destination = if (authRepository.session.value.canEnterHome) {
                MainDestination.Home
            } else {
                MainDestination.Login
            },
        )
    )

    /** 根界面唯一状态源，包含当前认证态和路由目标。 */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _uiState.update { state ->
                    // 认证态丢失时强制回登录；从登录页进入后默认跳到首页。
                    val nextDestination = when {
                        !session.canEnterHome -> MainDestination.Login
                        state.destination == MainDestination.Login -> MainDestination.Home
                        else -> state.destination
                    }
                    state.copy(
                        session = session,
                        destination = nextDestination,
                    )
                }
            }
        }
    }

    /** 打开首页；若当前认证态已失效则回退到登录页。 */
    fun openHome() {
        _uiState.update { state ->
            state.copy(
                destination = if (state.session.canEnterHome) {
                    MainDestination.Home
                } else {
                    MainDestination.Login
                }
            )
        }
    }

    /** 打开设置页；未登录或无权进入首页时统一回退到登录页。 */
    fun openSettings() {
        _uiState.update { state ->
            if (!state.session.canEnterHome) {
                state.copy(destination = MainDestination.Login)
            } else {
                state.copy(destination = MainDestination.Settings)
            }
        }
    }

    /** 强制切回登录页。 */
    fun openLogin() {
        _uiState.update { it.copy(destination = MainDestination.Login) }
    }

    /**
     * 跳转到首页并选中指定会话。
     *
     * 用于设置页添加智能体后自动跳转到对应聊天页。
     */
    fun openChat(sessionId: String) {
        if (sessionId.isBlank()) return
        _uiState.update { state ->
            state.copy(
                destination = if (state.session.canEnterHome) {
                    MainDestination.Home
                } else {
                    MainDestination.Login
                },
                pendingSessionId = sessionId,
            )
        }
    }

    /** 清除已消费的待选中会话 ID。 */
    fun consumePendingSessionId() {
        _uiState.update { state ->
            if (state.pendingSessionId == null) state
            else state.copy(pendingSessionId = null)
        }
    }

    /** 退出当前账号，并交由认证态监听回收首页/设置页访问权限。 */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}