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

    /** 切换会话置顶状态（置顶 / 取消置顶共用一个 intent，按 [pin] 区分方向）。 */
    data class SetSessionPinned(val sessionId: String, val pin: Boolean) : HomeIntent()

    /** 删除整条会话（联动清理收藏）。 */
    data class DeleteSession(val sessionId: String) : HomeIntent()

    /** 重命名会话标题。空标题由 ViewModel 直接拒绝。 */
    data class RenameSession(val sessionId: String, val newTitle: String) : HomeIntent()

    /** 消费一次性错误事件，避免重复展示。 */
    object ConsumeError : HomeIntent()
}