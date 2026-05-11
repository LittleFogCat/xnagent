package tech.xiaoniu.xnagent.ui.screen.home

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
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
interface HomeRepository {
    val sessions: Flow<List<Session>>

    fun loadModelConfig(): Flow<ModelConfig?>

    /** SSE 流式聊天，逐块返回 [SendToLLMResult.Streaming]，完成时流结束 */
    fun sendToLLM(request: ChatRequest): Flow<SendToLLMResult>

    fun getModels(): Flow<ModelsResponse>

    fun getAgents(): Flow<AgentsResponse>

    fun getChats(): Flow<ChatListResponse>

    fun getCurrentChat(id: String? = null): Flow<CurrentChatResponse>

    fun getChat(id: String): Flow<ChatResponse>

    fun createChat(request: CreateChatRequest): Flow<ChatResponse>

    fun updateChat(id: String, request: UpdateChatRequest): Flow<ChatResponse>

    fun deleteChat(id: String): Flow<DeleteChatResponse>
}