package tech.xiaoniu.xnagent.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.data.remote.dto.ChatTargetDto
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.data.repository.FavoriteRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import javax.inject.Inject

data class AgentUiState(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val added: Boolean = false,
)

data class SettingsUiState(
    val agents: List<AgentUiState> = emptyList(),
    val favorites: List<FavoriteMessage> = emptyList(),
    val noticeMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.favorites.collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }
        refreshAgents()
    }

    fun refreshAgents() {
        viewModelScope.launch {
            homeRepository.getAgents().catch {
                _uiState.update { state ->
                    state.copy(noticeMessage = "加载智能体失败，请稍后重试")
                }
            }.collect { response ->
                _uiState.update { state ->
                    state.copy(
                        agents = response.identities.map { agent ->
                            AgentUiState(
                                id = agent.id,
                                name = agent.name,
                                role = agent.role.orEmpty(),
                                description = agent.description.orEmpty(),
                                added = state.agents.any { it.id == agent.id && it.added },
                            )
                        }
                    )
                }
            }
        }
    }

    fun addAgentToChat(agentId: String) {
        val agent = _uiState.value.agents.firstOrNull { it.id == agentId } ?: return
        viewModelScope.launch {
            val models = runCatching { homeRepository.getModels().first() }.getOrNull()
            val defaultModelId = models?.defaultModel ?: models?.models?.firstOrNull()?.id

            runCatching {
                homeRepository.createChat(
                    CreateChatRequest(
                        title = agent.name,
                        model = defaultModelId,
                        chatTarget = ChatTargetDto(
                            type = "identity",
                            id = agent.id,
                        ),
                    )
                ).first()
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        agents = state.agents.map {
                            if (it.id == agent.id) it.copy(added = true) else it
                        },
                        noticeMessage = "已将 ${agent.name} 添加到聊天记录",
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(noticeMessage = "添加智能体失败，请稍后重试")
                }
            }
        }
    }

    fun removeFavorite(id: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(id)
        }
    }
}