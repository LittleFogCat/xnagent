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

    val sessions: List<SessionUiModel> = emptyList(),

    val isDeepThinkingEnabled: Boolean = false,

    val isLoggedIn: Boolean = false,

    val isGuest: Boolean = false,

    val guestUserMessageCount: Int = 0,

    val isGuestMessageLimitReached: Boolean = false,

    val viewerName: String = "",

    val viewerEmail: String = "",
)

/** 侧边栏中的单个会话摘要。 */
data class SessionUiModel(
    val id: String,

    val title: String,

    val lastMessageTime: Long,

    val selected: Boolean = false,
)

/** 抽屉按日期分组后的会话集合。 */
data class SessionGroup(
    val groupTitle: String,

    val sessions: List<SessionUiModel>
)