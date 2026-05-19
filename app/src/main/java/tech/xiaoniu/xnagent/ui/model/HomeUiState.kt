package tech.xiaoniu.xnagent.ui.model

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

/** Ui state for [tech.xiaoniu.xnagent.ui.screen.home.HomeScreen] **/
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

data class SessionUiModel(
    val id: String,
    val title: String,
    val lastMessageTime: Long,
    val selected: Boolean = false,
)

data class SessionGroup(
    val groupTitle: String,
    val sessions: List<SessionUiModel>
)