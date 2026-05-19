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
)

/**
 * 应用根状态。
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
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.session.collect { session ->
                _uiState.update { state ->
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

    fun openSettings() {
        _uiState.update { state ->
            if (!state.session.canEnterHome) {
                state.copy(destination = MainDestination.Login)
            } else {
                state.copy(destination = MainDestination.Settings)
            }
        }
    }

    fun openLogin() {
        _uiState.update { it.copy(destination = MainDestination.Login) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}