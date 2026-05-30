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
 * 旧的可选模型信息结构。
 *
 * 当前主流程实际使用 [ModelUiModel]，这里仍保留给兼容代码或预览数据使用。
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
 * 聊天消息 UI 模型。
 *
 * 同时承载正文、推理内容以及流式生成中的临时状态。
 */
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String = "",
    val reasoningDurationMs: Long? = null,
    val isThinking: Boolean = false,
    val isGenerating: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** 把 UI 消息转换成聊天接口请求使用的 DTO。 */
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

/** 把 UI 消息转换成旧的 LLMMessage 结构。 */
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