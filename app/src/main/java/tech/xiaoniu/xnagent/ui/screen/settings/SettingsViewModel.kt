package tech.xiaoniu.xnagent.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.data.remote.dto.ChatTargetDto
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.repository.AuthRepository
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
    val isClearingLocalData: Boolean = false,
)

/** 设置页一次性导航事件，用于把页面级动作上报给上层路由。 */
sealed interface SettingsEvent {
    /** 打开指定会话的聊天页。 */
    data class OpenChat(val sessionId: String) : SettingsEvent
}

/** 设置页状态管理，负责公开智能体、收藏列表和本地清理操作。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())

    /** 设置页唯一状态源，聚合智能体、收藏和本地清理提示。 */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)

    /** 设置页一次性事件流；上游订阅后触发跳转等副作用。 */
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.favorites.collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }
        // 智能体列表不依赖用户交互，页面初始化时即拉取一次。
        refreshAgents()
    }

    /** 刷新可添加到会话的智能体列表。 */
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

    /**
     * 将公开智能体创建成一条新的聊天会话。
     *
     * 这里会先推导默认模型，再调用创建聊天接口，成功后把条目标记为已添加。
     */
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
            }.onSuccess { chatResponse ->
                _uiState.update { state ->
                    state.copy(
                        agents = state.agents.map {
                            if (it.id == agent.id) it.copy(added = true) else it
                        },
                        noticeMessage = "已进入 ${agent.name} 的聊天页",
                    )
                }
                _events.tryEmit(SettingsEvent.OpenChat(chatResponse.chat.id))
            }.onFailure {
                _uiState.update { state ->
                    state.copy(noticeMessage = "添加智能体失败，请稍后重试")
                }
            }
        }
    }

    /** 从收藏列表中移除指定消息。 */
    fun removeFavorite(id: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(id)
        }
    }

    /**
     * 清除本地缓存数据并退出登录。
     *
     * 远端聊天记录不受影响，因此这里只重置本地数据库、收藏和认证态。
     */
    fun clearLocalData() {
        if (_uiState.value.isClearingLocalData) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    noticeMessage = null,
                    isClearingLocalData = true,
                )
            }

            runCatching {
                homeRepository.clearLocalChats()
                favoriteRepository.clearFavorites()
                authRepository.logout()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isClearingLocalData = false,
                        noticeMessage = "清除本地数据失败，请稍后重试",
                    )
                }
            }
        }
    }
}