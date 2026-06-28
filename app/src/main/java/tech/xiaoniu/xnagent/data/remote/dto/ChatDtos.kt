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

/** 聊天目标，可指向某个智能体等附加上下文。 */
@Serializable
data class ChatTargetDto(
    val type: String,
    val id: String
)

/**
 * 发给聊天接口的请求体。
 *
 * 同时覆盖普通聊天和流式聊天两种模式；当前应用默认开启 streaming。
 */
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

/** 控制模型推理模式。 */
@Serializable
data class ThinkingConfig(val type: Type) {
    enum class Type {
        @SerialName("enabled")
        ENABLED,

        @SerialName("disabled")
        DISABLED;
    }
}

/** 单个模型的展示信息。 */
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

/** 模型列表响应。 */
@Serializable
data class ModelsResponse(
    val models: List<ModelInfoDto> = emptyList(),
    val defaultModel: String? = null
)

/** 单个智能体信息。 */
@Serializable
data class AgentInfoDto(
    val id: String,
    val name: String,
    val role: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val free: Boolean = false
)

/** 智能体列表响应。 */
@Serializable
data class AgentsResponse(
    val identities: List<AgentInfoDto> = emptyList()
)

/** 聊天记录完整对象 */
@Serializable
data class ChatDto(
    val id: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val userId: String? = null,
    val title: String,
    val model: String,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val chatTarget: ChatTargetDto? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val messages: List<ChatMessageDto>? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val createdAt: Long? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val updatedAt: Long? = null,
    /** 是否置顶。旧版服务端不返回该字段时为 null，客户端以本地为准。 */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPinned: Boolean? = null,
)

/** 创建聊天记录请求体 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateChatRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val messages: List<ChatMessageDto>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val chatTarget: ChatTargetDto? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPinned: Boolean? = null,
)

/** 更新聊天记录请求体 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateChatRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val messages: List<ChatMessageDto>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val chatTarget: ChatTargetDto? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPinned: Boolean? = null,
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

/**
 * SSE 数据块。
 *
 * 服务端会把推理内容和正文拆成不同字段，客户端据此分别显示“思考中”和最终回答。
 */
@Serializable
data class SseChunk(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
)
