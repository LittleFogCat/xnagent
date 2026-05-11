package tech.xiaoniu.xnagent.ui.screen.home

import tech.xiaoniu.xnagent.ui.model.AgentMode
import tech.xiaoniu.xnagent.ui.model.ModelUiModel

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
sealed class HomeIntent {
    object Initialize : HomeIntent()
    data class SetAgentMode(val mode: AgentMode) : HomeIntent()
    data class UpdateInput(val text: String) : HomeIntent()
    object SendMessage : HomeIntent()
    data class AppendAssistantMessage(val content: String) : HomeIntent()
    data class SelectModel(val model: ModelUiModel) : HomeIntent()
}