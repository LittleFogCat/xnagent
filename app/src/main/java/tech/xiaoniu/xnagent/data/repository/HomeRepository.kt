package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.Flow
import tech.xiaoniu.xnagent.data.ModelConfig
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.remote.dto.AgentsResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatListResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.CurrentChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.DeleteChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.ModelsResponse
import tech.xiaoniu.xnagent.data.remote.dto.UpdateChatRequest
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult

/**
 * 聊天主页的数据入口。
 *
 * 该接口同时承载模型列表、聊天会话、SSE 对话流以及本地/远端会话持久化能力。
 */
interface HomeRepository {
    /** 当前本地已缓存的会话列表。 */
    val sessions: Flow<List<Session>>

    /** 读取本地模型配置，用于离线兜底。 */
    fun loadModelConfig(): Flow<ModelConfig?>

    /** SSE 流式聊天，逐块返回 [SendToLLMResult.Streaming]，完成时流结束 */
    fun sendToLLM(request: ChatRequest): Flow<SendToLLMResult>

    /** 拉取当前可用模型和默认模型。 */
    fun getModels(): Flow<ModelsResponse>

    /** 拉取服务端公开的智能体列表。 */
    fun getAgents(): Flow<AgentsResponse>

    /** 拉取当前账号的聊天列表。 */
    fun getChats(): Flow<ChatListResponse>

    /**
     * 拉取当前聊天或指定聊天详情。
     *
     * @param id 为空时由服务端返回“当前聊天”；不为空时返回指定聊天。
     */
    fun getCurrentChat(id: String? = null): Flow<CurrentChatResponse>

    /**
     * 拉取指定聊天详情。
     *
     * @param id 聊天会话 ID。
     */
    fun getChat(id: String): Flow<ChatResponse>

    /**
     * 创建新的远端聊天会话。
     *
     * @param request 聊天标题、模型和目标智能体等创建参数。
     */
    fun createChat(request: CreateChatRequest): Flow<ChatResponse>

    /**
     * 更新远端聊天会话。
     *
     * @param id 聊天会话 ID。
     * @param request 需要更新的聊天属性。
     */
    fun updateChat(id: String, request: UpdateChatRequest): Flow<ChatResponse>

    /**
     * 删除远端聊天会话。
     *
     * @param id 聊天会话 ID。
     */
    fun deleteChat(id: String): Flow<DeleteChatResponse>

    /**
     * 读取当前选中会话。
     *
     * @param useRemote 为 true 时优先从服务端读取，否则读取本地缓存。
     */
    suspend fun loadCurrentStoredChat(useRemote: Boolean): StoredChat?

    /**
     * 读取指定会话的完整聊天内容。
     *
     * @param sessionId 会话 ID。
     * @param useRemote 为 true 时优先从服务端读取，否则读取本地缓存。
     */
    suspend fun loadStoredChat(sessionId: String, useRemote: Boolean): StoredChat?

    /**
     * 保存当前聊天内容。
     *
     * @param sessionId 现有会话 ID；为空时由仓库创建新会话。
     * @param modelId 当前会话关联的模型 ID。
     * @param messages 需要持久化的消息列表。
     * @param useRemote 为 true 时同步到服务端，否则仅保存到本地。
     */
    suspend fun saveStoredChat(
        sessionId: String?,
        modelId: String,
        messages: List<ChatMessage>,
        useRemote: Boolean,
    ): StoredChat

    /** 清空本地缓存的聊天数据。 */
    suspend fun clearLocalChats()

    /** 将游客期或离线期的本地聊天同步到远端账号。 */
    suspend fun syncLocalChatsToRemote()
}