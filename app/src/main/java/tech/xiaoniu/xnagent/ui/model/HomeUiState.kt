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
    val currentModel: ModelUiModel? = null,
    val availableModels: List<ModelUiModel> = emptyList(),
    val sessions: List<SessionUiModel> = emptyList()
)

data class SessionUiModel(
    val id: String,
    val title: String,
    val lastMessageTime: Long
)

data class SessionGroup(
    val groupTitle: String,
    val sessions: List<SessionUiModel>
)