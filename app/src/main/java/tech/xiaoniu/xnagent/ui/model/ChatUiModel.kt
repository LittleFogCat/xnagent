package tech.xiaoniu.xnagent.ui.model

import tech.xiaoniu.xnagent.data.LLMMessage
import tech.xiaoniu.xnagent.data.remote.dto.ChatMessageDto

/**
 * Agent 工作模式
 */
enum class AgentMode(val displayName: String) {
    ASK("Ask"),
    PLAN("Plan"),
    BUILD("Agent")
}

/**
 * 可选模型信息
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String = ""
)

/**
 * 聊天消息角色
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * 聊天消息 UI 模型
 */
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toChatMessageDto(): ChatMessageDto {
        return ChatMessageDto(
            role = when (role) {
                MessageRole.SYSTEM -> "system"
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
            },
            content = content
        )
    }
}

fun ChatMessage.toLLMMessage(): LLMMessage {
    return LLMMessage(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        content = content
    )
}