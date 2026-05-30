package tech.xiaoniu.xnagent.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ThinkingConfig
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.data.repository.FavoriteRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import tech.xiaoniu.xnagent.data.repository.StoredChat
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.HomeUiState
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.ModelUiModel
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult
import tech.xiaoniu.xnagent.ui.model.SessionUiModel
import java.util.UUID
import javax.inject.Inject

/**
 * 主页面状态管理。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val tag = javaClass.simpleName
    private val _uiState = MutableStateFlow(HomeUiState())

    /** 主页唯一状态源，界面上的会话、输入和消息流都从这里派生。 */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var initialized = false

    private companion object {
        const val GUEST_USER_MESSAGE_LIMIT = 10
    }

    /**
     * 主页统一意图分发入口，负责把 UI 事件路由到对应的状态变更或异步任务。
     */
    fun dispatch(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Initialize -> initialize()
            is HomeIntent.SetAgentMode -> _uiState.update { it.copy(agentMode = intent.mode) }
            is HomeIntent.UpdateInput -> _uiState.update { it.copy(inputText = intent.text) }
            HomeIntent.SendMessage -> sendNewMessage()
            is HomeIntent.SelectModel -> _uiState.update {
                it.copy(
                    currentModel = intent.model,
                    currentSessionModelId = intent.model.id,
                )
            }

            is HomeIntent.SelectSession -> loadSession(intent.sessionId)
            HomeIntent.CreateNewChat -> startNewChat()
            HomeIntent.ToggleDeepThinking -> _uiState.update {
                it.copy(isDeepThinkingEnabled = !it.isDeepThinkingEnabled)
            }

            is HomeIntent.EditUserMessage -> editUserMessage(intent.messageId, intent.content)
            is HomeIntent.RegenerateAssistantMessage -> regenerateAssistantMessage(intent.messageId)
            is HomeIntent.DeleteMessage -> deleteMessage(intent.messageId)
            is HomeIntent.FavoriteMessage -> favoriteMessage(intent.messageId)
        }
    }

    /**
     * 完成首页首次初始化。
     *
     * 首次进入时注册模型、认证态和收藏监听；后续只在已登录场景补拉远端会话。
     */
    private fun initialize() {
        // 首次进入时只注册一次观察者；后续回到页面只补一次远端会话刷新。
        if (initialized) {
            if (authRepository.session.value.isLoggedIn) {
                refreshRemoteSessions()
            }
            return
        }
        initialized = true
        observeModels()
        observeAuthState()
        observeFavorites()
    }

    /** 监听可用模型列表，并尽量保持当前会话的模型选择不被刷新覆盖。 */
    private fun observeModels() {
        viewModelScope.launch {
            homeRepository.getModels().catch {
                Log.w(tag, "observeModels: ${it.stackTraceToString()}")
            }.collect { response ->
                val modelUiModels = response.models.map { model ->
                    ModelUiModel(model.id, model.name, model.provider)
                }
                if (modelUiModels.isEmpty()) return@collect

                // 优先维持当前会话已选模型，其次退回服务端默认模型，避免刷新后模型跳变。
                val preferredModelId = _uiState.value.currentSessionModelId
                val defaultModel = response.defaultModel?.let { defaultId ->
                    modelUiModels.find { it.id == defaultId || it.fullId == defaultId }
                } ?: modelUiModels.first()
                val selectedModel = preferredModelId?.let { modelId ->
                    modelUiModels.find { it.id == modelId || it.fullId == modelId }
                } ?: _uiState.value.currentModel ?: defaultModel

                _uiState.update {
                    it.copy(
                        availableModels = modelUiModels,
                        currentModel = selectedModel,
                    )
                }
            }
        }
    }

    /** 监听认证态变化，切换本地/远端会话源并同步侧边栏身份信息。 */
    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                // 认证态变化时同步更新侧边栏展示信息，并重新计算游客发送次数限制。
                _uiState.update {
                    it.copy(
                        isLoggedIn = session.isLoggedIn,
                        isGuest = session.isGuest,
                        viewerName = session.user?.username?.substringBefore('@').orEmpty(),
                        viewerEmail = session.user?.email.orEmpty(),
                    ).withConversation(it.messages)
                }

                if (!session.canEnterHome) {
                    _uiState.update {
                        it.copy(
                            currentSessionId = null,
                            sessions = emptyList(),
                        ).withConversation(emptyList())
                    }
                    return@collectLatest
                }

                if (session.isLoggedIn) {
                    // 登录后先尝试把游客/离线聊天补传到远端，再以远端会话列表为准。
                    runCatching {
                        homeRepository.syncLocalChatsToRemote()
                    }.onFailure {
                        Log.w(tag, "observeAuthState: syncLocalChatsToRemote failed", it)
                    }
                    refreshRemoteSessions()
                } else {
                    // 游客模式下直接订阅本地数据库中的会话列表。
                    homeRepository.sessions.collect { sessions ->
                        applySessionList(sessions.map { it.toSessionUiModel() })
                    }
                }
            }
        }
    }

    /** 同步收藏 ID 集合，供消息列表直接判断是否已收藏。 */
    private fun observeFavorites() {
        viewModelScope.launch {
            favoriteRepository.favorites.collectLatest { favorites ->
                _uiState.update {
                    it.copy(favoriteMessageIds = favorites.map { favorite -> favorite.id }.toSet())
                }
            }
        }
    }

    /** 从服务端刷新会话列表，并映射成侧边栏所需的 UI 模型。 */
    private fun refreshRemoteSessions() {
        viewModelScope.launch {
            homeRepository.getChats().catch {
                Log.w(tag, "refreshRemoteSessions: ${it.stackTraceToString()}")
            }.collect { response ->
                applySessionList(
                    response.chats.map {
                        SessionUiModel(
                            id = it.id,
                            title = it.title,
                            lastMessageTime = it.updatedAt ?: it.createdAt ?: System.currentTimeMillis(),
                        )
                    }
                )
            }
        }
    }

    /** 将新的会话列表应用到状态中，并尽量保留当前选中的会话。 */
    private fun applySessionList(sessions: List<SessionUiModel>) {
        // 会话列表刷新时尽量保留用户当前选中项，只有找不到时才回退到第一条。
        val currentSessionId = _uiState.value.currentSessionId
        val nextSessionId = when {
            currentSessionId != null && sessions.any { it.id == currentSessionId } -> currentSessionId
            sessions.isNotEmpty() -> sessions.first().id
            else -> null
        }

        _uiState.update { state ->
            state.copy(
                currentSessionId = nextSessionId,
                sessions = sessions.map { it.copy(selected = it.id == nextSessionId) },
            ).withConversation(if (nextSessionId == null) emptyList() else state.messages)
        }

        if (nextSessionId == null) {
            _uiState.update { it.withConversation(emptyList()) }
            return
        }

        if (currentSessionId != nextSessionId || _uiState.value.messages.isEmpty()) {
            loadSession(nextSessionId)
        }
    }

    /** 按当前登录态读取指定会话内容，并同步选中项、模型和消息列表。 */
    private fun loadSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                homeRepository.loadStoredChat(
                    sessionId = sessionId,
                    useRemote = authRepository.session.value.isLoggedIn,
                )
            }.onSuccess { storedChat ->
                storedChat ?: return@onSuccess
                applyStoredChat(storedChat)
            }.onFailure {
                Log.w(tag, "loadSession: sessionId=$sessionId", it)
            }
        }
    }

    /** 清空当前上下文，进入一条尚未发送的新会话。 */
    private fun startNewChat() {
        _uiState.update { state ->
            state.copy(
                currentSessionId = null,
                currentSessionModelId = state.currentModel?.id,
                sessions = state.sessions.map { it.copy(selected = false) },
            ).withConversation(emptyList())
        }
    }

    /** 将输入框中的文本转换成一条新用户消息，并启动一次完整对话流。 */
    private fun sendNewMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGuestMessageLimitReached) return

        // 先把用户消息乐观写入本地状态，随后统一走 sendConversation 持久化并请求模型。
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
        )
        val baseMessages = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(
                inputText = "",
            ).withConversation(baseMessages)
        }
        sendConversation(baseMessages)
    }

    /** 编辑历史用户消息后，从该消息处截断上下文并重新生成后续回答。 */
    private fun editUserMessage(messageId: String, content: String) {
        val updatedContent = content.trim()
        if (updatedContent.isBlank()) return

        // 编辑历史用户消息时，后续上下文已失效，因此从该消息开始截断并重新生成回答。
        val currentMessages = _uiState.value.messages
        val targetIndex = currentMessages.indexOfFirst {
            it.id == messageId && it.role == MessageRole.USER
        }
        if (targetIndex < 0) return

        val editedMessage = currentMessages[targetIndex].copy(
            content = updatedContent,
            timestamp = System.currentTimeMillis(),
        )
        val baseMessages = currentMessages.take(targetIndex) + editedMessage
        _uiState.update { it.withConversation(baseMessages) }
        sendConversation(baseMessages)
    }

    /** 重新生成指定助手消息，并保留它之前最近一条用户消息及其上下文。 */
    private fun regenerateAssistantMessage(messageId: String) {
        val currentMessages = _uiState.value.messages
        val assistantIndex = currentMessages.indexOfFirst {
            it.id == messageId && it.role == MessageRole.ASSISTANT
        }
        if (assistantIndex < 0) return

        val userIndex = (assistantIndex - 1 downTo 0).firstOrNull {
            currentMessages[it].role == MessageRole.USER
        } ?: return

        // 重新生成时保留“上一条用户消息及其之前的上下文”，移除旧回答后重新请求。
        val baseMessages = currentMessages.take(userIndex + 1)
        _uiState.update { it.withConversation(baseMessages) }
        sendConversation(baseMessages)
    }

    /** 删除指定消息，并将更新后的会话内容重新持久化。 */
    private fun deleteMessage(messageId: String) {
        val currentMessages = _uiState.value.messages
        if (currentMessages.none { it.id == messageId }) return

        val updatedMessages = currentMessages.filterNot { it.id == messageId }
        persistConversation(updatedMessages)
    }

    /** 将指定消息加入收藏，并补齐会话标题等展示所需元信息。 */
    private fun favoriteMessage(messageId: String) {
        val state = _uiState.value
        if (state.favoriteMessageIds.contains(messageId)) return

        val message = state.messages.firstOrNull { it.id == messageId } ?: return
        val sessionTitle = state.sessions.firstOrNull { it.id == state.currentSessionId }?.title
            ?: state.messages.firstOrNull { it.role == MessageRole.USER }?.content?.take(24)
            ?: "当前会话"

        viewModelScope.launch {
            favoriteRepository.addFavorite(
                FavoriteMessage(
                    id = message.id,
                    sessionId = state.currentSessionId,
                    sessionTitle = sessionTitle,
                    role = message.role.name,
                    content = message.content.ifBlank { message.reasoningContent },
                    timestamp = message.timestamp,
                )
            )
        }
    }

    /** 将当前会话内容持久化到本地或远端，并回写仓库规范化后的会话状态。 */
    private fun persistConversation(messages: List<ChatMessage>) {
        val currentModel = _uiState.value.currentModel
        if (currentModel == null) {
            _uiState.update { it.withConversation(messages) }
            return
        }

        val currentSessionId = _uiState.value.currentSessionId
        val useRemote = authRepository.session.value.isLoggedIn
        viewModelScope.launch {
            val storedChat = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = currentSessionId,
                    modelId = currentModel.id,
                    messages = messages,
                    useRemote = useRemote,
                )
            }.onFailure {
                Log.w(tag, "persistConversation: save chat failed", it)
            }.getOrNull()

            if (storedChat != null) {
                applyStoredChat(storedChat, messagesOverride = messages)
            } else {
                _uiState.update {
                    it.copy(currentSessionId = currentSessionId).withConversation(messages)
                }
            }
        }
    }

    /**
     * 保存当前上下文并发起一次完整的流式对话请求。
     *
     * 该流程会先落盘用户侧消息，再把 SSE 返回的思考片段和正文片段折叠成同一条助手消息。
     */
    private fun sendConversation(baseMessages: List<ChatMessage>) {
        val currentModel = _uiState.value.currentModel ?: return
        val useRemote = authRepository.session.value.isLoggedIn

        viewModelScope.launch {
            // 先保存用户侧消息，确保刷新或切后台后仍能恢复到“待回复”状态。
            val savedBaseChat = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = _uiState.value.currentSessionId,
                    modelId = currentModel.id,
                    messages = baseMessages,
                    useRemote = useRemote,
                )
            }.onFailure {
                Log.w(tag, "sendConversation: save base chat failed", it)
            }.getOrNull() ?: return@launch

            applyStoredChat(savedBaseChat, messagesOverride = baseMessages)
            val persistedSessionId = savedBaseChat.sessionId
            val request = ChatRequest(
                model = currentModel.id,
                messages = baseMessages.map { it.toChatMessageDto() },
                thinking = ThinkingConfig(
                    if (_uiState.value.isDeepThinkingEnabled) {
                        ThinkingConfig.Type.ENABLED
                    } else {
                        ThinkingConfig.Type.DISABLED
                    }
                ),
            )

            var conversation = baseMessages
            var assistantMessageId: String? = null
            var accumulatedContent = ""
            var accumulatedReasoning = ""
            var thinkingStartedAtMs: Long? = null
            var isThinking = false
            var isGenerating = false

            // 将 SSE 中“思考中”和“正文生成中”两段状态折叠成一条可持续刷新的助手消息。
            fun buildAssistantMessage(): ChatMessage {
                val thinkingDurationMs = thinkingStartedAtMs?.let { startedAt ->
                    if (accumulatedReasoning.isBlank()) {
                        null
                    } else {
                        (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                    }
                }

                return ChatMessage(
                    id = assistantMessageId ?: UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = accumulatedContent,
                    reasoningContent = accumulatedReasoning,
                    reasoningDurationMs = if (accumulatedReasoning.isBlank()) null else thinkingDurationMs,
                    isThinking = isThinking,
                    isGenerating = isGenerating,
                )
            }

            // 每次流式片段到达时都就地更新最后一条助手消息，避免消息列表不断新增占位项。
            fun upsertAssistantMessage() {
                if (assistantMessageId == null) {
                    assistantMessageId = UUID.randomUUID().toString()
                }
                val assistantMessage = buildAssistantMessage().copy(id = assistantMessageId!!)
                conversation = conversation.upsertAssistantMessage(assistantMessage)
                _uiState.update { state ->
                    state.copy(
                        currentSessionId = persistedSessionId,
                    ).withConversation(conversation)
                }
            }

            homeRepository.sendToLLM(request).collect { result ->
                when (result) {
                    is SendToLLMResult.Thinking -> {
                        Log.d(tag, "sendToLLM: ${currentTimeF()} thinking...: ${result.content}")
                        // reasoning 片段用于展示“思考过程”，正文尚未开始生成。
                        if (thinkingStartedAtMs == null) {
                            thinkingStartedAtMs = System.currentTimeMillis()
                        }
                        isThinking = true
                        isGenerating = true
                        accumulatedReasoning += result.content
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Streaming -> {
                        Log.d(tag, "sendToLLM: ${currentTimeF()} streaming...: ${result.content}")
                        // 正文流到来后关闭 thinking 标记，但仍保持 generating 直到流结束。
                        accumulatedContent += result.content
                        isThinking = false
                        isGenerating = true
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Error -> {
                        Log.w(tag, "sendToLLM: response error", result.error)
                        accumulatedContent = "哎呀，好像出错了！错误信息：${result.error.message}"
                        isThinking = false
                        isGenerating = false
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Success -> {
                        isThinking = false
                        isGenerating = false
                        upsertAssistantMessage()
                        Log.i(tag, "sendToLLM: onSuccess")
                    }
                }
            }

            // 某些服务端流可能没有显式终态事件，这里做一次兜底收尾。
            if (assistantMessageId != null && (isThinking || isGenerating)) {
                isThinking = false
                isGenerating = false
                upsertAssistantMessage()
            }

            val finalStoredChat = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = persistedSessionId,
                    modelId = currentModel.id,
                    messages = conversation,
                    useRemote = useRemote,
                )
            }.onFailure {
                Log.w(tag, "sendConversation: save final chat failed", it)
            }.getOrNull()

            if (finalStoredChat != null) {
                applyStoredChat(finalStoredChat, messagesOverride = conversation)
            } else if (useRemote) {
                refreshRemoteSessions()
            }
        }
    }

    /** 使用仓库返回的会话快照统一刷新当前会话 ID、模型选择和消息列表。 */
    private fun applyStoredChat(storedChat: StoredChat, messagesOverride: List<ChatMessage>? = null) {
        val selectedModel = findModel(storedChat.modelId)
        _uiState.update { state ->
            state.copy(
                currentSessionId = storedChat.sessionId,
                currentSessionModelId = storedChat.modelId,
                currentModel = selectedModel ?: state.currentModel,
                sessions = state.sessions.map { it.copy(selected = it.id == storedChat.sessionId) },
            ).withConversation(messagesOverride ?: storedChat.messages)
        }

        if (authRepository.session.value.isLoggedIn) {
            refreshRemoteSessions()
        }
    }

    private fun findModel(modelId: String): ModelUiModel? {
        return _uiState.value.availableModels.find { it.id == modelId || it.fullId == modelId }
    }

    private fun Session.toSessionUiModel(): SessionUiModel {
        return SessionUiModel(
            id = id,
            title = title,
            lastMessageTime = updateTime,
        )
    }

    private fun List<ChatMessage>.upsertAssistantMessage(message: ChatMessage): List<ChatMessage> {
        val currentMessages = toMutableList()
        val index = currentMessages.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            currentMessages[index] = message
        } else {
            currentMessages.add(message)
        }
        return currentMessages
    }

    private fun HomeUiState.withConversation(messages: List<ChatMessage>): HomeUiState {
        // 游客发送次数属于派生状态，每次消息列表变化时统一回算，避免多个入口各自维护。
        val userMessageCount = messages.count { it.role == MessageRole.USER }
        return copy(
            messages = messages,
            guestUserMessageCount = userMessageCount,
            isGuestMessageLimitReached = isGuest && userMessageCount >= GUEST_USER_MESSAGE_LIMIT,
        )
    }
}