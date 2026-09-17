package tech.xiaoniu.xnagent.ui.model

/**
 * 消息定位信息，用于收藏跳转后滚动到目标消息并闪烁高亮。
 *
 * 远端消息 ID 每次加载都会按 `"$sessionId-$index-${role.hashCode()}-${content.hashCode()}"`
 * 重算，因此 [messageId] 可能对不上；[role] + [content] 作为兜底匹配依据。
 */
data class MessageHighlight(
    val messageId: String,
    val role: MessageRole,
    val content: String,
)
