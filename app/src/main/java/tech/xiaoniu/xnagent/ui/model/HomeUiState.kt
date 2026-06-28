package tech.xiaoniu.xnagent.ui.model

/**
 * 首页状态。
 *
 * 包含当前会话、消息列表、模型选择、游客限制和侧边栏用户信息。
 */
data class HomeUiState(
    val agentMode: AgentMode = AgentMode.ASK,

    val inputText: String = "",

    val messages: List<ChatMessage> = emptyList(),

    val favoriteMessageIds: Set<String> = emptySet(),

    val currentModel: ModelUiModel? = null,

    val currentSessionId: String? = null,

    val currentSessionModelId: String? = null,

    val availableModels: List<ModelUiModel> = emptyList(),

    val availableAgents: List<AgentUiModel> = emptyList(),

    val sessions: List<SessionUiModel> = emptyList(),

    val isDeepThinkingEnabled: Boolean = false,

    val isLoggedIn: Boolean = false,

    val isGuest: Boolean = false,

    val guestUserMessageCount: Int = 0,

    val isGuestMessageLimitReached: Boolean = false,

    val viewerName: String = "",

    val viewerEmail: String = "",

    /** 是否有请求正在等待或接收 SSE 响应。UI 据此禁用发送按钮、显示状态提示。 */
    val isResponding: Boolean = false,

    /** 标题生成中的中间态。新会话发出首条消息后置 true，期间顶部栏展示加载指示。 */
    val isGeneratingTitle: Boolean = false,

    /**
     * 一次性错误事件。UI 渲染 Snackbar/Toast 后调用 [tech.xiaoniu.xnagent.ui.screen.home.HomeIntent.ConsumeError]
     * 清空，避免重复消费。
     */
    val errorMessage: String? = null,
)

/**
 * 侧边栏中的单个会话摘要。
 *
 * [agentId] / [agentName] 由远端 `chatTarget` 与已缓存的智能体列表合并而来，
 * 仅在智能体会话上有值；展示时通过 [displayTitle] 与 [isAgent] 派生。
 */
data class SessionUiModel(
    val id: String,

    val title: String,

    val isPinned: Boolean,

    val agentId: String? = null,

    val agentName: String? = null,

    val updatedAt: Long,

    val selected: Boolean = false,
) {
    /** 是否为绑定智能体的会话。 */
    val isAgent: Boolean get() = !agentId.isNullOrBlank()

    /** 抽屉展示文本：智能体用其名称，否则用标题。 */
    val displayTitle: String get() = agentName ?: title
}

/**
 * 智能体 UI 结构。
 *
 * 由远端 `AgentInfoDto` 转换而来，用于抽屉条目显示智能体名称。当前只用 id / name；
 * [tech.xiaoniu.xnagent.data.remote.dto.AgentInfoDto.avatarUrl] 暂未渲染——待引入图片加载库后再补。
 */
data class AgentUiModel(
    val id: String,
    val name: String,
)
