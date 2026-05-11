package tech.xiaoniu.xnagent.ui.screen.home

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.ModelConfig
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.api.StreamChatApi
import tech.xiaoniu.xnagent.data.remote.dto.AgentsResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatListResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.CurrentChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.DeleteChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.ModelsResponse
import tech.xiaoniu.xnagent.data.remote.dto.SseChunk
import tech.xiaoniu.xnagent.data.remote.dto.UpdateChatRequest
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult
import javax.inject.Inject

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
class HomeRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    private val streamChatApi: StreamChatApi,
    private val chatApi: ChatApi
) : HomeRepository {
    private val TAG = javaClass.simpleName

    private val appContext: Context = context

    override fun loadModelConfig() = flow {
        val modelConfig = appContext.assets
            .open("model_config.json")
            .bufferedReader()
            .use { reader ->
                ModelConfig.fromJson(reader.readText())
            }
        emit(modelConfig)
    }.catch {
        Log.e(TAG, "loadModelConfig: ", it)
        emit(null)
    }

    // ---- SSE 流式聊天 ----

    override fun sendToLLM(request: ChatRequest): Flow<SendToLLMResult> = flow {
        val responseBody = try {
            streamChatApi.chat(request)
        } catch (e: Exception) {
            Log.w(TAG, "sendToLLM: request failed", e)
            emit(SendToLLMResult.Error(e))
            return@flow
        }
        try {
            responseBody.byteStream()
                .bufferedReader()
                .use {
                    var line: String? = null
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        line = it.readLine()
                        if (line.isBlank()) continue

                        when {
                            line.startsWith(":") -> {
                                Log.d(TAG, "Received SSE comment: $line")
                                // do nothing
                            }

                            line.trim() == "data: [DONE]" -> {
                                Log.d(TAG, "Stream completed")
                                break
                            }

                            line.startsWith("data: ") -> {
                                val data = line.substring("data: ".length)
                                Log.d(TAG, "sendToLLM: ${currentTimeF()} received chunk: $data")
                                runCatching {
                                    json.decodeFromString(SseChunk.serializer(), data).content
                                }.onSuccess { content ->
                                    emit(SendToLLMResult.Streaming(content))
                                }.onFailure { e ->
                                    Log.w(TAG, "sendToLLM: parse chunk failed", e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "sendToLLM: read response failed", e)
            emit(SendToLLMResult.Error(e))
        } finally {
            try {
                responseBody.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    // ---- 会话列表 ----

    override val sessions: Flow<List<Session>> = emptyFlow()

    // ---- 公开元数据 ----

    override fun getModels(): Flow<ModelsResponse> = flow {
        emit(chatApi.getModels())
    }.flowOn(Dispatchers.IO)

    override fun getAgents(): Flow<AgentsResponse> = flow {
        emit(chatApi.getAgents())
    }.flowOn(Dispatchers.IO)

    // ---- 聊天记录 CRUD ----

    override fun getChats(): Flow<ChatListResponse> = flow {
        emit(chatApi.getChats())
    }.flowOn(Dispatchers.IO)

    override fun getCurrentChat(id: String?): Flow<CurrentChatResponse> = flow {
        emit(chatApi.getCurrentChat(id))
    }.flowOn(Dispatchers.IO)

    override fun getChat(id: String): Flow<ChatResponse> = flow {
        emit(chatApi.getChat(id))
    }.flowOn(Dispatchers.IO)

    override fun createChat(request: CreateChatRequest): Flow<ChatResponse> = flow {
        emit(chatApi.createChat(request))
    }.flowOn(Dispatchers.IO)

    override fun updateChat(id: String, request: UpdateChatRequest): Flow<ChatResponse> = flow {
        emit(chatApi.updateChat(id, request))
    }.flowOn(Dispatchers.IO)

    override fun deleteChat(id: String): Flow<DeleteChatResponse> = flow {
        emit(chatApi.deleteChat(id))
    }.flowOn(Dispatchers.IO)
}
