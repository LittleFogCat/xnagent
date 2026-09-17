package tech.xiaoniu.xnagent.ui.screen.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.data.repository.AgentRepository
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.data.repository.FavoriteRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import tech.xiaoniu.xnagent.data.repository.toMessageRoleEnum
import tech.xiaoniu.xnagent.ui.model.AgentUiModel
import tech.xiaoniu.xnagent.ui.model.MessageHighlight
import javax.inject.Inject

data class SettingsUiState(
    /** 合并后的智能体清单（含隐藏项，不含已删除项）。界面按 `isHidden` 自行分组。 */
    val agents: List<AgentUiModel> = emptyList(),
    val favorites: List<FavoriteMessage> = emptyList(),
    val noticeMessage: String? = null,
    val isClearingLocalData: Boolean = false,
)

/** 设置页一次性导航事件，用于把页面级动作上报给上层路由。 */
sealed interface SettingsEvent {
    /** 打开收藏对应的会话，并高亮定位到该条消息。 */
    data class OpenFavorite(val sessionId: String, val highlight: MessageHighlight) : SettingsEvent
}

/** 设置页状态管理，负责智能体清单、收藏列表和本地清理操作。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val favoriteRepository: FavoriteRepository,
    private val agentRepository: AgentRepository,
) : ViewModel() {
    private val tag = javaClass.simpleName

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
        viewModelScope.launch {
            // 智能体清单由仓库合并服务端与本地行，订阅即可拿到新建 / 改名 / 隐藏的结果。
            agentRepository.agents.collect { agents ->
                _uiState.update { it.copy(agents = agents) }
            }
        }
        // 页面初始化时拉一次服务端清单与绑定关系，「已添加」徽标才准确。
        refreshAgents()
    }

    /** 刷新服务端智能体清单与「已添加」绑定关系。 */
    fun refreshAgents() {
        viewModelScope.launch {
            runCatching { agentRepository.refresh() }
                .onFailure {
                    Log.w(tag, "refreshAgents failed", it)
                    _uiState.update { state -> state.copy(noticeMessage = "加载智能体失败，请稍后重试") }
                }
            runCatching { agentRepository.refreshBindings() }
                .onFailure { Log.w(tag, "refreshAgents: refreshBindings failed", it) }
        }
    }

    /** 切换智能体的隐藏状态。隐藏只在列表不展示，可恢复。 */
    fun setAgentHidden(agentId: String, hidden: Boolean) {
        viewModelScope.launch {
            runCatching { agentRepository.setAgentHidden(agentId, hidden) }
                .onFailure {
                    Log.w(tag, "setAgentHidden: agentId=$agentId hidden=$hidden", it)
                    _uiState.update { state -> state.copy(noticeMessage = "操作失败，请稍后重试") }
                }
        }
    }

    /**
     * 打开收藏对应的会话并高亮定位。
     *
     * 收藏的 `sessionId` 可能为空（历史数据），或指向已被删除的会话——前者在这里挡住，
     * 后者交给主页在加载失败时给出提示。
     */
    fun openFavorite(favorite: FavoriteMessage) {
        val sessionId = favorite.sessionId?.takeIf { it.isNotBlank() }
        if (sessionId == null) {
            _uiState.update { it.copy(noticeMessage = "该收藏未关联会话，无法跳转") }
            return
        }
        _events.tryEmit(
            SettingsEvent.OpenFavorite(
                sessionId = sessionId,
                highlight = MessageHighlight(
                    messageId = favorite.id,
                    role = favorite.role.toMessageRoleEnum(),
                    content = favorite.content,
                ),
            )
        )
    }

    /** 从收藏列表中移除指定消息。 */
    fun removeFavorite(id: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(id)
        }
    }

    /** 消费一次性提示，避免旋转屏幕 / 重组时重复弹出。 */
    fun consumeNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    /**
     * 清除本地缓存数据并退出登录。
     *
     * 远端聊天记录不受影响，因此这里只重置本地数据库、智能体、收藏和认证态。
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
                agentRepository.clearAgents()
                favoriteRepository.clearFavorites()
                authRepository.logout()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isClearingLocalData = false,
                        noticeMessage = "清除本地数据失败，请稍后重试"
                    )
                }
            }
        }
    }
}
