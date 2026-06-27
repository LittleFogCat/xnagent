package tech.xiaoniu.xnagent.ui.screen.home

import tech.xiaoniu.xnagent.ui.model.AgentMode
import tech.xiaoniu.xnagent.ui.model.ModelUiModel

/**
 * 首页意图集合。
 *
 * ViewModel 只接收这一组离散事件，避免界面直接调用内部方法。
 */
sealed class HomeIntent {
    object Initialize : HomeIntent()

    data class SetAgentMode(val mode: AgentMode) : HomeIntent()

    data class UpdateInput(val text: String) : HomeIntent()

    object SendMessage : HomeIntent()

    object CancelMessage : HomeIntent()

    data class SelectModel(val model: ModelUiModel) : HomeIntent()

    data class SelectSession(val sessionId: String) : HomeIntent()

    object CreateNewChat : HomeIntent()

    object ToggleDeepThinking : HomeIntent()

    data class EditUserMessage(val messageId: String, val content: String) : HomeIntent()

    data class RegenerateAssistantMessage(val messageId: String) : HomeIntent()

    data class DeleteMessage(val messageId: String) : HomeIntent()

    data class FavoriteMessage(val messageId: String) : HomeIntent()
}