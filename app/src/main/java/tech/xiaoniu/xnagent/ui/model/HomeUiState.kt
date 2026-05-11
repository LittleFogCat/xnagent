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
    val availableModels: List<ModelUiModel> = emptyList()
)