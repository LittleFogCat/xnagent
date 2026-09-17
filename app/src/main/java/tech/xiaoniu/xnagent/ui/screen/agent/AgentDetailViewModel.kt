package tech.xiaoniu.xnagent.ui.screen.agent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.data.remote.dto.ChatTargetDto
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.repository.AgentRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import tech.xiaoniu.xnagent.ui.model.AgentUiModel
import javax.inject.Inject

/**
 * 智能体详情页状态。
 *
 * 表单只暴露需求要求的三个可编辑字段（名称 / 简介 / 提示词）；[role] 是服务端下发的只读定位。
 */
data class AgentDetailUiState(
    /** 当前编辑的智能体 ID；null 表示尚未保存过的新建智能体。 */
    val agentId: String? = null,
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val prompt: String = "",
    /** 纯本地自定义智能体（服务端不存在该 identity）。 */
    val isCustom: Boolean = false,
    /** 对服务端智能体的本地覆盖。 */
    val isOverridden: Boolean = false,
    val isHidden: Boolean = false,
    val boundSessionId: String? = null,
    val isLoading: Boolean = false,
    /** 保存 / 建会话 / 删除等写操作进行中，用于禁用按钮避免重复触发。 */
    val isBusy: Boolean = false,
    val noticeMessage: String? = null,
) {
    /** 名称是必填项，也是唯一允许触发保存的必要条件。 */
    val canSave: Boolean get() = name.isNotBlank() && !isBusy
}

/** 智能体详情页一次性事件。 */
sealed interface AgentDetailEvent {
    /** 打开（或新建）该智能体的会话。 */
    data class OpenChat(val sessionId: String) : AgentDetailEvent

    /** 智能体已删除，需要退回上一页。 */
    data object Closed : AgentDetailEvent
}

/**
 * 智能体详情页状态管理。
 *
 * 表单内容在 [AgentDetailIntent.Load] 时从 [AgentRepository] 的合并结果读取，
 * 保存时写回本地 `agent` 表；同一 identity 的会话只会创建一条（服务端限制）。
 */
@HiltViewModel
class AgentDetailViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val homeRepository: HomeRepository,
) : ViewModel() {
    private val tag = javaClass.simpleName

    private val _uiState = MutableStateFlow(AgentDetailUiState())

    /** 详情页唯一状态源。 */
    val uiState: StateFlow<AgentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgentDetailEvent>(extraBufferCapacity = 1)

    /** 详情页一次性事件流；上层订阅后触发跳转。 */
    val events: SharedFlow<AgentDetailEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    fun dispatch(intent: AgentDetailIntent) {
        when (intent) {
            is AgentDetailIntent.Load -> load(intent.agentId)
            is AgentDetailIntent.UpdateName -> _uiState.update { it.copy(name = intent.name) }
            is AgentDetailIntent.UpdateDescription -> _uiState.update { it.copy(description = intent.description) }
            is AgentDetailIntent.UpdatePrompt -> _uiState.update { it.copy(prompt = intent.prompt) }
            AgentDetailIntent.Save -> save()
            AgentDetailIntent.OpenChat -> openChat()
            is AgentDetailIntent.SetHidden -> setHidden(intent.hidden)
            AgentDetailIntent.Delete -> delete()
            AgentDetailIntent.ConsumeNotice -> _uiState.update { it.copy(noticeMessage = null) }
        }
    }

    /**
     * 载入表单内容。
     *
     * 每次都整体重置状态：Activity 级 `hiltViewModel()` 会让同一个 ViewModel 实例在不同
     * `agentId` 之间复用，不重置会把上一个智能体的编辑内容串到下一个。
     */
    private fun load(agentId: String?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = AgentDetailUiState(agentId = agentId, isLoading = agentId != null)
            if (agentId == null) return@launch

            // 智能体清单可能还没拉回来（刚进设置页就点进来），补一次刷新再读。
            val agent = agentRepository.getAgent(agentId) ?: run {
                runCatching { agentRepository.refresh() }
                    .onFailure { Log.w(tag, "load: refresh failed", it) }
                agentRepository.getAgent(agentId)
            }
            if (agent == null) {
                _uiState.update { it.copy(isLoading = false, noticeMessage = "智能体不存在或已删除") }
                return@launch
            }
            _uiState.value = agent.toUiState()
        }
    }

    /** 保存表单。名称必填，保存后把落库结果（含新生成的 ID）回写到状态。 */
    private fun save() {
        val state = _uiState.value
        if (state.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, noticeMessage = null) }
            val savedId = persistDraft()
            if (savedId == null) {
                _uiState.update { it.copy(isBusy = false) }
                return@launch
            }
            val saved = agentRepository.getAgent(savedId)
            _uiState.update { current ->
                if (saved == null) {
                    current.copy(isBusy = false, agentId = savedId, noticeMessage = "已保存")
                } else {
                    saved.toUiState().copy(isBusy = false, noticeMessage = "已保存")
                }
            }
        }
    }

    /**
     * 进入该智能体的会话。
     *
     * 已有绑定会话时直接打开——服务端同一 identity 只允许一条会话，重复创建会返回 500，
     * 这正是「已添加的智能体点了没反应」的成因。
     */
    private fun openChat() {
        val state = _uiState.value
        if (state.isBusy) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "请先填写智能体名称") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, noticeMessage = null) }

            // 未保存的新智能体先落库，否则没有可绑定的 ID。
            val agentId = state.agentId?.takeIf { it.isNotBlank() } ?: persistDraft()
            if (agentId == null) {
                _uiState.update { it.copy(isBusy = false) }
                return@launch
            }
            val agent = agentRepository.getAgent(agentId)
            if (agent == null) {
                _uiState.update { it.copy(isBusy = false, noticeMessage = "智能体不存在或已删除") }
                return@launch
            }

            agent.boundSessionId?.takeIf { it.isNotBlank() }?.let { existing ->
                _uiState.update { it.copy(isBusy = false) }
                _events.tryEmit(AgentDetailEvent.OpenChat(existing))
                return@launch
            }

            val defaultModelId = runCatching { homeRepository.getModels().first() }.getOrNull()
                ?.let { response -> response.defaultModel ?: response.models.firstOrNull()?.id }

            val created = runCatching {
                homeRepository.createChat(
                    CreateChatRequest(
                        title = agent.name,
                        model = defaultModelId,
                        // 自定义智能体在服务端没有对应 identity，传 chatTarget 会被拒；
                        // 其人格由请求体的 system 消息携带（见 HomeViewModel.sendConversation）。
                        chatTarget = if (agent.isCustom) {
                            null
                        } else {
                            ChatTargetDto(type = "identity", id = agent.id)
                        },
                    )
                ).first()
            }.onFailure {
                Log.w(tag, "openChat: createChat failed", it)
            }.getOrNull()

            if (created == null) {
                _uiState.update { it.copy(isBusy = false, noticeMessage = "创建会话失败，请稍后重试") }
                return@launch
            }
            if (agent.isCustom) {
                // 自定义智能体的会话在远端没有 chatTarget，绑定关系只能存本地。
                agentRepository.bindSession(agent.id, created.chat.id)
            }
            _uiState.update { it.copy(isBusy = false, boundSessionId = created.chat.id) }
            _events.tryEmit(AgentDetailEvent.OpenChat(created.chat.id))
        }
    }

    private fun setHidden(hidden: Boolean) {
        val agentId = _uiState.value.agentId ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, noticeMessage = null) }
            runCatching { agentRepository.setAgentHidden(agentId, hidden) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, isHidden = hidden) }
                }
                .onFailure {
                    Log.w(tag, "setHidden: agentId=$agentId hidden=$hidden", it)
                    _uiState.update { it.copy(isBusy = false, noticeMessage = "操作失败，请稍后重试") }
                }
        }
    }

    private fun delete() {
        val agentId = _uiState.value.agentId ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, noticeMessage = null) }
            runCatching { agentRepository.deleteAgent(agentId) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false) }
                    _events.tryEmit(AgentDetailEvent.Closed)
                }
                .onFailure {
                    Log.w(tag, "delete: agentId=$agentId", it)
                    _uiState.update { it.copy(isBusy = false, noticeMessage = "删除失败，请稍后重试") }
                }
        }
    }

    /**
     * 把当前表单写入本地。
     *
     * @return 落库后的智能体 ID；名称为空或写入失败时返回 null，并已写入 [AgentDetailUiState.noticeMessage]。
     */
    private suspend fun persistDraft(): String? {
        val state = _uiState.value
        val name = state.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(noticeMessage = "请先填写智能体名称") }
            return null
        }
        return runCatching {
            agentRepository.saveAgent(
                AgentUiModel(
                    id = state.agentId.orEmpty(),
                    name = name,
                    role = state.role,
                    description = state.description,
                    prompt = state.prompt,
                    isCustom = state.isCustom,
                )
            )
        }.onFailure {
            Log.w(tag, "persistDraft: saveAgent failed", it)
        }.getOrNull() ?: run {
            _uiState.update { it.copy(noticeMessage = "保存失败，请稍后重试") }
            null
        }
    }

    private fun AgentUiModel.toUiState(): AgentDetailUiState = AgentDetailUiState(
        agentId = id,
        name = name,
        role = role,
        description = description,
        prompt = prompt,
        isCustom = isCustom,
        isOverridden = isOverridden,
        isHidden = isHidden,
        boundSessionId = boundSessionId,
    )
}
