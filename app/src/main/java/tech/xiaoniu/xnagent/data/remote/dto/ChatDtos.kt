package tech.xiaoniu.xnagent.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.xiaoniu.xnagent.data.remote.dto.ThinkingConfig.Type

/** 请求用的消息 DTO */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatTargetDto(
    val type: String,
    val id: String
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val chatTarget: ChatTargetDto? = null,
    @SerialName("max_tokens") val maxTokens: Long? = null,
    val temperature: Double? = null,
    val thinking: ThinkingConfig? = ThinkingConfig(Type.ENABLED),
    @SerialName("top_p") val topP: Double? = null,
    val streaming: Boolean = true
)

@Serializable
data class ThinkingConfig(val type: Type) {

    enum class Type {
        @SerialName("enabled")
        ENABLED,

        @SerialName("disabled")
        DISABLED;
    }
}

@Serializable
data class ModelInfoDto(
    val id: String,
    val name: String,
    val provider: String,
    val free: Boolean = false,
    val reasoning: Boolean = false,
    val contextWindow: Long? = null,
    val maxTokens: Int? = null
)

@Serializable
data class ModelsResponse(
    val models: List<ModelInfoDto> = emptyList(),
    val defaultModel: String? = null
)

@Serializable
data class AgentInfoDto(
    val id: String,
    val name: String,
    val role: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val free: Boolean = false
)

@Serializable
data class AgentsResponse(
    val identities: List<AgentInfoDto> = emptyList()
)

/** 聊天记录完整对象 */
@Serializable
data class ChatDto(
    val id: String,
    val userId: String,
    val title: String,
    val model: String,
    val chatTarget: ChatTargetDto? = null,
    val messages: List<ChatMessageDto>? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

/** 创建聊天记录请求体 */
@Serializable
data class CreateChatRequest(
    val title: String? = null,
    val model: String? = null,
    val messages: List<ChatMessageDto>? = null,
    val chatTarget: ChatTargetDto? = null
)

/** 更新聊天记录请求体 */
@Serializable
data class UpdateChatRequest(
    val title: String? = null,
    val model: String? = null,
    val messages: List<ChatMessageDto>? = null,
    val chatTarget: ChatTargetDto? = null
)

/** GET /api/chats 返回 */
@Serializable
data class ChatListResponse(
    val chats: List<ChatDto> = emptyList()
)

/** GET /api/chats/current 返回 */
@Serializable
data class CurrentChatResponse(
    val chat: ChatDto? = null
)

/** GET /api/chats/:id、POST /api/chats、PUT /api/chats/:id 返回 */
@Serializable
data class ChatResponse(
    val chat: ChatDto
)

/** DELETE /api/chats/:id 返回 */
@Serializable
data class DeleteChatResponse(
    val success: Boolean
)

/** SSE 数据块 **/
@Serializable
data class SseChunk(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
)
