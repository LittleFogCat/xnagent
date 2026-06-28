package tech.xiaoniu.xnagent.data.repository

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import tech.xiaoniu.xnagent.common.Constants
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.ModelConfig
import tech.xiaoniu.xnagent.data.local.dao.ChatDao
import tech.xiaoniu.xnagent.data.local.entity.ChatMessage as LocalChatMessage
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.local.PendingRetryOp
import tech.xiaoniu.xnagent.data.local.PendingRetryQueue
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.api.StreamChatApi
import tech.xiaoniu.xnagent.data.remote.dto.AgentsResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatListResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatMessageDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.CurrentChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.DeleteChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.ModelsResponse
import tech.xiaoniu.xnagent.data.remote.dto.SseChunk
import tech.xiaoniu.xnagent.data.remote.dto.ThinkingConfig
import tech.xiaoniu.xnagent.data.remote.dto.UpdateChatRequest
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult
import java.util.UUID
import javax.inject.Inject

/**
 * 主页数据仓库实现。
 *
 * 同时负责三类数据来源：
 * 1. 模型、智能体、聊天 CRUD 等远端接口；
 * 2. SSE 流式聊天响应解析；
 * 3. 本地 Room 会话缓存，以及登录后向远端的同步。
 */
class HomeRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    private val streamChatApi: StreamChatApi,
    private val chatApi: ChatApi,
    private val chatDao: ChatDao,
    private val favoriteRepository: FavoriteRepository,
    private val pendingRetryQueue: PendingRetryQueue,
) : HomeRepository {
    private val TAG = javaClass.simpleName

    private val appContext: Context = context

    /** 从 assets 读取本地模型配置，作为服务端列表不可用时的兜底。 */
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
        Log.i(TAG, "sendToLLM: ${json.encodeToString(ChatRequest.serializer(), request)}")
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
                                // SSE 心跳或注释行，无需进入业务解析。
                            }

                            line.trim() == "data: [DONE]" -> {
                                // 显式终态标记，表示服务端已经结束推流。
                                Log.d(TAG, "Stream completed")
                                break
                            }

                            line.startsWith("data: ") -> {
                                val data = line.substring("data: ".length)
                                Log.d(TAG, "sendToLLM: ${currentTimeF()} received chunk: $data")
                                runCatching {
                                    val chunk = json.decodeFromString(SseChunk.serializer(), data)
                                    chunk
                                }.onSuccess { chunk ->
                                    // 服务端把 reasoning 和正文拆成两类片段，UI 层会分别累积显示。
                                    if (chunk.reasoningContent != null) {
                                        emit(SendToLLMResult.Thinking(chunk.reasoningContent))
                                    } else if (chunk.content != null) {
                                        emit(SendToLLMResult.Streaming(chunk.content))
                                    } else {
                                        Log.w(TAG, "sendToLLM: Invalid chunk received: $data")
                                    }
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

    /**
     * 通过现有 `/api/chat` 流式接口调用 LLM，把首条用户消息提炼为标题。
     *
     * 复用 [sendToLLM] 的解析思路，但只关心正文片段，遇到首帧正文就开启累积，遇到 `[DONE]` 收尾。
     * 标题生成只会产生单串最终结果，因此暴露为挂起函数比 Flow 更稳定。
     */
    override suspend fun generateTitle(firstUserMessage: String, modelId: String): String = withContext(Dispatchers.IO) {
        val request = buildSummaryRequest(firstUserMessage, modelId)
        val responseBody = try {
            streamChatApi.chat(request)
        } catch (e: Exception) {
            Log.w(TAG, "generateTitle: request failed", e)
            return@withContext sanitizeTitle("")
        }
        val raw = StringBuilder()
        try {
            responseBody.byteStream()
                .bufferedReader()
                .use { reader ->
                    var line: String?
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        when {
                            line.startsWith(":") -> { /* SSE 注释，忽略 */ }
                            line.trim() == "data: [DONE]" -> break
                            line.startsWith("data: ") -> {
                                val data = line.substring("data: ".length)
                                runCatching {
                                    json.decodeFromString(SseChunk.serializer(), data)
                                }.onSuccess { chunk ->
                                    chunk.content?.let { raw.append(it) }
                                }.onFailure { e ->
                                    Log.w(TAG, "generateTitle: parse chunk failed", e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "generateTitle: read response failed", e)
        } finally {
            try {
                responseBody.close()
            } catch (_: Exception) {
            }
        }
        sanitizeTitle(raw.toString())
    }

    // ---- 会话列表 ----

    override val sessions: Flow<List<Session>> = chatDao.querySessionList()

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

    override suspend fun loadCurrentStoredChat(useRemote: Boolean): StoredChat? = withContext(Dispatchers.IO) {
        if (useRemote) {
            chatApi.getCurrentChat().chat?.toStoredChat()
        } else {
            // 本地模式下默认取最近一条会话作为“当前会话”。
            val session = chatDao.getSessionList().firstOrNull() ?: return@withContext null
            session.toStoredChat(chatDao.getChatMessagesBySessionId(session.id))
        }
    }

    override suspend fun loadStoredChat(sessionId: String, useRemote: Boolean): StoredChat? = withContext(Dispatchers.IO) {
        if (useRemote) {
            // ChatResponse.chat 由 Retrofit/序列化保证非空；远端 404 由 OkHttp 层抛 HttpException，
            // 不会进到这一步。生产中若需要处理「远端会话被同步删」的场景，应在 api 层返回 ChatResponse{chat=null}，
            // 再调整本方法。
            chatApi.getChat(sessionId).chat.toStoredChat()
        } else {
            val session = chatDao.getSession(sessionId)
            if (session == null) {
                Log.w(TAG, "loadStoredChat: session=$sessionId not found in local db")
                null
            } else {
                session.toStoredChat(chatDao.getChatMessagesBySessionId(session.id))
            }
        }
    }

    override suspend fun saveStoredChat(
        sessionId: String?,
        title: String?,
        modelId: String,
        messages: List<ChatMessage>,
        useRemote: Boolean,
    ): StoredChat = withContext(Dispatchers.IO) {
        // 标题语义：
        // - 新建（sessionId == null）：由 ViewModel 在串行流程中提前生成，这里直接写入。
        // - 更新（sessionId != null）：走 partial update，title 字段不传，依赖后端 / 本地保留原标题。
        //   避免流式落盘 / 编辑 / 重生 / 删除消息等后续路径把 LLM 生成的标题冲成「新对话」。
        val requestMessages = messages.map { it.toChatMessageDto() }
        if (useRemote) {
            val response = if (sessionId.isNullOrBlank()) {
                chatApi.createChat(
                    CreateChatRequest(
                        title = title ?: Constants.DEFAULT_TITLE,
                        model = modelId,
                        messages = requestMessages,
                    )
                )
            } else {
                chatApi.updateChat(
                    sessionId,
                    UpdateChatRequest(
                        // partial update：不传 title，依赖后端保留远端标题。
                        model = modelId,
                        messages = requestMessages,
                    )
                )
            }
            response.chat.toStoredChat()
        } else {
            val now = System.currentTimeMillis()
            val localSessionId = sessionId ?: UUID.randomUUID().toString()
            // 已有会话保留本地原标题 / createTime，避免流式落盘冲掉 LLM 标题。
            val existing = if (sessionId != null) chatDao.getSession(sessionId) else null
            // replaceSessionMessages 会整体替换该会话消息，适合编辑/重生后的截断重写。
            val session = Session(
                id = localSessionId,
                title = existing?.title ?: title ?: Constants.DEFAULT_TITLE,
                modelId = modelId,
                createTime = existing?.createTime ?: now,
                updateTime = now,
            )
            chatDao.replaceSessionMessages(
                session = session,
                messages = messages.mapIndexed { index, message ->
                    message.toLocalEntity(
                        sessionId = localSessionId,
                        fallbackIndex = index,
                    )
                }
            )
            session.toStoredChat(chatDao.getChatMessagesBySessionId(localSessionId))
        }
    }

    /**
     * 切换会话置顶状态。本地优先落盘，远端失败入队 [PendingRetryQueue] 等待下次刷新时重试。
     */
    override suspend fun pinSession(sessionId: String, isPinned: Boolean, useRemote: Boolean) = withContext(Dispatchers.IO) {
        chatDao.updateSessionPinned(sessionId, isPinned)
        if (useRemote) {
            runCatching {
                chatApi.updateChat(sessionId, UpdateChatRequest(isPinned = isPinned))
            }.onFailure {
                Log.w(TAG, "pinSession: remote update failed for $sessionId", it)
                pendingRetryQueue.add(PendingRetryOp.Pin(sessionId, isPinned))
            }
        }
    }

    /**
     * 重命名会话标题。本地优先落盘，同时刷新 updateTime 以触发列表重排。
     */
    override suspend fun renameSession(sessionId: String, newTitle: String, useRemote: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chatDao.updateSessionTitle(sessionId, newTitle, now)
        if (useRemote) {
            runCatching {
                chatApi.updateChat(sessionId, UpdateChatRequest(title = newTitle))
            }.onFailure {
                Log.w(TAG, "renameSession: remote update failed for $sessionId", it)
                pendingRetryQueue.add(PendingRetryOp.Rename(sessionId, newTitle))
            }
        }
    }

    /**
     * 删除会话。本地事务删除消息与会话，远端调用删除接口，最后级联清理收藏。
     */
    override suspend fun deleteSession(sessionId: String, useRemote: Boolean) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionWithMessages(sessionId)
        if (useRemote) {
            runCatching {
                chatApi.deleteChat(sessionId)
            }.onFailure {
                Log.w(TAG, "deleteSession: remote delete failed for $sessionId", it)
                pendingRetryQueue.add(PendingRetryOp.Delete(sessionId))
            }
        }
        favoriteRepository.removeFavoritesBySessionId(sessionId)
    }

    /**
     * 重试队列中所有待重试操作；成功后出队，失败保留等待下次。
     * 由 HomeViewModel 在 refreshRemoteSessions 之前触发。
     */
    override suspend fun retryPendingOperations() = withContext(Dispatchers.IO) {
        pendingRetryQueue.operations.value.forEach { op ->
            runCatching {
                when (op) {
                    is PendingRetryOp.Pin ->
                        chatApi.updateChat(op.sessionId, UpdateChatRequest(isPinned = op.isPinned))
                    is PendingRetryOp.Rename ->
                        chatApi.updateChat(op.sessionId, UpdateChatRequest(title = op.newTitle))
                    is PendingRetryOp.Delete ->
                        chatApi.deleteChat(op.sessionId)
                }
            }.onSuccess {
                pendingRetryQueue.remove(op)
            }.onFailure {
                Log.w(TAG, "retryPendingOperations: op=$op failed", it)
            }
        }
    }

    override suspend fun getLocalPinnedSessionIds(): Set<String> = withContext(Dispatchers.IO) {
        chatDao.getSessionList()
            .asSequence()
            .filter { it.isPinned }
            .map { it.id }
            .toSet()
    }

    override suspend fun syncLocalChatsToRemote() = withContext(Dispatchers.IO) {
        val localSessions = chatDao.getSessionList()
        localSessions.forEach { session ->
            runCatching {
                val messages = chatDao.getChatMessagesBySessionId(session.id)
                if (messages.isNotEmpty()) {
                    // 只有存在消息内容时才创建远端会话，避免产生空聊天壳。
                    chatApi.createChat(
                        CreateChatRequest(
                            title = session.title,
                            model = session.modelId,
                            messages = messages.map { it.toChatMessageDto() },
                            isPinned = session.isPinned,
                        )
                    )
                }
                // 同步成功后删除本地副本，避免后续重复上传。
                chatDao.deleteChatMessagesBySessionId(session.id)
                chatDao.deleteSession(session.id)
            }.onFailure {
                Log.w(TAG, "syncLocalChatsToRemote: session=${session.id}", it)
            }
        }
    }

    override suspend fun clearLocalChats() = withContext(Dispatchers.IO) {
        chatDao.clearChatMessages()
        chatDao.clearSessions()
    }

    /** 把远端聊天 DTO 转成界面统一使用的存储模型。 */
    private fun ChatDto.toStoredChat(): StoredChat {
        val remoteMessages = messages.orEmpty().mapIndexed { index, message ->
            message.toUiChatMessage(
                sessionId = id,
                fallbackIndex = index,
            )
        }
        return StoredChat(
            sessionId = id,
            title = title,
            modelId = model,
            messages = remoteMessages,
            updatedAt = updatedAt ?: createdAt ?: System.currentTimeMillis(),
        )
    }

    /** 把本地 Session 与消息列表拼装为统一的存储模型。 */
    private fun Session.toStoredChat(messages: List<LocalChatMessage>): StoredChat {
        return StoredChat(
            sessionId = id,
            title = title,
            modelId = modelId,
            messages = messages.map { it.toUiChatMessage() },
            updatedAt = updateTime,
        )
    }

    /** 把 UI 消息转成 Room 实体，保证本地会话落盘时 ID 稳定可追踪。 */
    private fun ChatMessage.toLocalEntity(sessionId: String, fallbackIndex: Int): LocalChatMessage {
        val localId = id.ifBlank { "$sessionId-$fallbackIndex-${UUID.randomUUID()}" }
        return LocalChatMessage(
            id = localId,
            sessionId = sessionId,
            role = role.toApiRole(),
            content = content,
            reasoningContent = reasoningContent,
            reasoningDurationMs = reasoningDurationMs,
            createTime = timestamp,
        )
    }

    /** 把本地数据库消息恢复成界面消息模型。 */
    private fun LocalChatMessage.toUiChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            role = role.toMessageRole(),
            content = content,
            reasoningContent = reasoningContent,
            reasoningDurationMs = reasoningDurationMs,
            timestamp = createTime,
        )
    }

    /** 远端聊天更新接口只接收基础 role/content，这里做最小投影。 */
    private fun LocalChatMessage.toChatMessageDto(): ChatMessageDto {
        return ChatMessageDto(
            role = role,
            content = content,
        )
    }

    /** 为远端消息生成稳定的临时 ID，方便 UI 列表 diff。 */
    private fun ChatMessageDto.toUiChatMessage(sessionId: String, fallbackIndex: Int): ChatMessage {
        return ChatMessage(
            id = "$sessionId-$fallbackIndex-${role.hashCode()}-${content.hashCode()}",
            role = role.toMessageRole(),
            content = content,
        )
    }

    /** 将界面角色枚举映射到接口约定的 role 字符串。 */
    private fun MessageRole.toApiRole(): String {
        return when (this) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        }
    }

    /** 将远端或本地持久化的 role 字符串恢复成界面枚举。 */
    private fun String.toMessageRole(): MessageRole {
        return when (lowercase()) {
            "system" -> MessageRole.SYSTEM
            "assistant" -> MessageRole.ASSISTANT
            else -> MessageRole.USER
        }
    }

    /** 拼装标题生成请求。 */
    private fun buildSummaryRequest(content: String, modelId: String): ChatRequest {
        return ChatRequest(
            model = modelId,
            messages = listOf(
                ChatMessageDto(
                    role = "system",
                    content = SUMMARY_SYSTEM_PROMPT,
                ),
                ChatMessageDto(
                    role = "user",
                    content = content,
                ),
            ),
            thinking = ThinkingConfig(ThinkingConfig.Type.DISABLED),
        )
    }

    /**
     * 清洗模型输出：去空白 / 包裹引号 / markdown 代码块围栏；
     * 长度 > [TITLE_MAX_LENGTH] 时截断；空结果回退到 [Constants.DEFAULT_TITLE]。
     */
    private fun sanitizeTitle(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return Constants.DEFAULT_TITLE

        // 去掉 markdown 代码块围栏与首尾成对引号。
        text = text.removePrefix("```").removeSuffix("```").trim()
        if ((text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("'") && text.endsWith("'")) ||
            (text.startsWith("「") && text.endsWith("」")) ||
            (text.startsWith("『") && text.endsWith("』"))) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (text.isEmpty()) return Constants.DEFAULT_TITLE
        return if (text.length <= TITLE_MAX_LENGTH) text else text.take(TITLE_MAX_LENGTH)
    }

    private companion object {
        /** 标题生成 system prompt：要求 LLM 输出 8~15 字纯文本。 */
        const val SUMMARY_SYSTEM_PROMPT =
            "请把以下用户消息提炼为一个 8 到 15 字的简洁中文标题，" +
                "不要使用标点符号、引号或额外说明，只输出标题本身。"

        /** 标题最大长度，与需求文档保持一致。 */
        const val TITLE_MAX_LENGTH = 15
    }
}
