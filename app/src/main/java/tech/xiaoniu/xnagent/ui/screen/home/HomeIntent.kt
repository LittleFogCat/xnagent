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
    data class SelectModel(val model: ModelUiModel) : HomeIntent()
    data class SelectSession(val sessionId: String) : HomeIntent()
    object CreateNewChat : HomeIntent()
    object ToggleDeepThinking : HomeIntent()
    data class EditUserMessage(val messageId: String, val content: String) : HomeIntent()
    data class RegenerateAssistantMessage(val messageId: String) : HomeIntent()
    data class DeleteMessage(val messageId: String) : HomeIntent()
    data class FavoriteMessage(val messageId: String) : HomeIntent()
}