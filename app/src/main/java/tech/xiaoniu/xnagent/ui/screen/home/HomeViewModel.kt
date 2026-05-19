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
) : ViewModel() {
    private val tag = javaClass.simpleName
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var initialized = false

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
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        observeModels()
        observeAuthState()
    }

    private fun observeModels() {
        viewModelScope.launch {
            homeRepository.getModels().catch {
                Log.w(tag, "observeModels: ${it.stackTraceToString()}")
            }.collect { response ->
                val modelUiModels = response.models.map { model ->
                    ModelUiModel(model.id, model.name, model.provider)
                }
                if (modelUiModels.isEmpty()) return@collect

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

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.session.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = session.isLoggedIn,
                        isGuest = session.isGuest,
                    )
                }

                if (!session.canEnterHome) {
                    _uiState.update {
                        it.copy(
                            currentSessionId = null,
                            sessions = emptyList(),
                            messages = emptyList(),
                        )
                    }
                    return@collectLatest
                }

                if (session.isLoggedIn) {
                    runCatching {
                        homeRepository.syncLocalChatsToRemote()
                    }.onFailure {
                        Log.w(tag, "observeAuthState: syncLocalChatsToRemote failed", it)
                    }
                    refreshRemoteSessions()
                } else {
                    homeRepository.sessions.collect { sessions ->
                        applySessionList(sessions.map { it.toSessionUiModel() })
                    }
                }
            }
        }
    }

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

    private fun applySessionList(sessions: List<SessionUiModel>) {
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
                messages = if (nextSessionId == null) emptyList() else state.messages,
            )
        }

        if (nextSessionId == null) {
            _uiState.update { it.copy(messages = emptyList()) }
            return
        }

        if (currentSessionId != nextSessionId || _uiState.value.messages.isEmpty()) {
            loadSession(nextSessionId)
        }
    }

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

    private fun startNewChat() {
        _uiState.update { state ->
            state.copy(
                currentSessionId = null,
                currentSessionModelId = state.currentModel?.id,
                messages = emptyList(),
                sessions = state.sessions.map { it.copy(selected = false) },
            )
        }
    }

    private fun sendNewMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
        )
        val baseMessages = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(
                inputText = "",
                messages = baseMessages,
            )
        }
        sendConversation(baseMessages)
    }

    private fun editUserMessage(messageId: String, content: String) {
        val updatedContent = content.trim()
        if (updatedContent.isBlank()) return

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
        _uiState.update { it.copy(messages = baseMessages) }
        sendConversation(baseMessages)
    }

    private fun regenerateAssistantMessage(messageId: String) {
        val currentMessages = _uiState.value.messages
        val assistantIndex = currentMessages.indexOfFirst {
            it.id == messageId && it.role == MessageRole.ASSISTANT
        }
        if (assistantIndex < 0) return

        val userIndex = (assistantIndex - 1 downTo 0).firstOrNull {
            currentMessages[it].role == MessageRole.USER
        } ?: return

        val baseMessages = currentMessages.take(userIndex + 1)
        _uiState.update { it.copy(messages = baseMessages) }
        sendConversation(baseMessages)
    }

    private fun sendConversation(baseMessages: List<ChatMessage>) {
        val currentModel = _uiState.value.currentModel ?: return
        val useRemote = authRepository.session.value.isLoggedIn

        viewModelScope.launch {
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
                )
            }

            fun upsertAssistantMessage() {
                if (assistantMessageId == null) {
                    assistantMessageId = UUID.randomUUID().toString()
                }
                val assistantMessage = buildAssistantMessage().copy(id = assistantMessageId!!)
                conversation = conversation.upsertAssistantMessage(assistantMessage)
                _uiState.update { state ->
                    state.copy(
                        currentSessionId = persistedSessionId,
                        messages = conversation,
                    )
                }
            }

            homeRepository.sendToLLM(request).collect { result ->
                when (result) {
                    is SendToLLMResult.Thinking -> {
                        Log.d(tag, "sendToLLM: ${currentTimeF()} thinking...: ${result.content}")
                        if (thinkingStartedAtMs == null) {
                            thinkingStartedAtMs = System.currentTimeMillis()
                        }
                        isThinking = true
                        accumulatedReasoning += result.content
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Streaming -> {
                        Log.d(tag, "sendToLLM: ${currentTimeF()} streaming...: ${result.content}")
                        accumulatedContent += result.content
                        isThinking = false
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Error -> {
                        Log.w(tag, "sendToLLM: response error", result.error)
                        accumulatedContent = "哎呀，好像出错了！错误信息：${result.error.message}"
                        isThinking = false
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Success -> {
                        isThinking = false
                        Log.i(tag, "sendToLLM: onSuccess")
                    }
                }
            }

            if (assistantMessageId != null && isThinking) {
                isThinking = false
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

    private fun applyStoredChat(storedChat: StoredChat, messagesOverride: List<ChatMessage>? = null) {
        val selectedModel = findModel(storedChat.modelId)
        _uiState.update { state ->
            state.copy(
                currentSessionId = storedChat.sessionId,
                currentSessionModelId = storedChat.modelId,
                currentModel = selectedModel ?: state.currentModel,
                messages = messagesOverride ?: storedChat.messages,
                sessions = state.sessions.map { it.copy(selected = it.id == storedChat.sessionId) },
            )
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
}