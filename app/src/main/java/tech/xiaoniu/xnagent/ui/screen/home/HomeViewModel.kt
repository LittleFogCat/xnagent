package tech.xiaoniu.xnagent.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.common.Constants
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.remote.dto.AgentInfoDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ThinkingConfig
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.data.repository.FavoriteRepository
import tech.xiaoniu.xnagent.data.repository.HomeRepository
import tech.xiaoniu.xnagent.data.repository.StoredChat
import tech.xiaoniu.xnagent.ui.model.AgentUiModel
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

    /**
     * 当前进行中的流式对话请求；用于在用户点击停止按钮时取消协程，
     * 让 [tech.xiaoniu.xnagent.data.repository.HomeRepositoryImpl.sendToLLM] 中的 `ensureActive()` 抛出并断开 SSE。
     */
    private var sendJob: Job? = null

    /**
     * 当前进行中的会话加载请求；用户快速切换会话时取消前一个，
     * 避免慢响应的旧会话覆盖新会话的消息列表。
     */
    private var loadSessionJob: Job? = null

    /**
     * 当前进行中的标题生成协程。用户在标题生成期间切换会话 / 退出时取消，避免脏写。
     */
    private var generateTitleJob: Job? = null

    internal companion object {
        /** 游客模式单会话用户消息上限；UI 文案与本常量保持一致。 */
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
            HomeIntent.CancelMessage -> cancelMessage()
            is HomeIntent.SelectModel -> _uiState.update {
                it.copy(
                    currentModel = intent.model,
                    currentSessionModelId = intent.model.id,
                )
            }

            is HomeIntent.SelectSession -> {
                // 切换会话时取消进行中的标题生成，避免标题完成时把已选会话的 ID 覆盖回新会话。
                generateTitleJob?.cancel()
                _uiState.update { state ->
                    state.copy(
                        currentSessionId = intent.sessionId,
                        sessions = state.sessions.map { it.copy(selected = it.id == intent.sessionId) },
                    ).withConversation(emptyList())
                }
                loadSession(intent.sessionId)
            }
            HomeIntent.CreateNewChat -> startNewChat()
            HomeIntent.ToggleDeepThinking -> _uiState.update {
                it.copy(isDeepThinkingEnabled = !it.isDeepThinkingEnabled)
            }

            is HomeIntent.EditUserMessage -> editUserMessage(intent.messageId, intent.content)
            is HomeIntent.RegenerateAssistantMessage -> regenerateAssistantMessage(intent.messageId)
            is HomeIntent.DeleteMessage -> deleteMessage(intent.messageId)
            is HomeIntent.FavoriteMessage -> favoriteMessage(intent.messageId)

            is HomeIntent.SetSessionPinned -> setSessionPinned(intent.sessionId, intent.pin)
            is HomeIntent.DeleteSession -> deleteSession(intent.sessionId)
            is HomeIntent.RenameSession -> renameSession(intent.sessionId, intent.newTitle)
            HomeIntent.ConsumeError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    /**
     * 完成首页首次初始化。
     *
     * 首次进入时注册模型、认证态、智能体和收藏监听；后续只在已登录场景补拉远端会话。
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
        observeAgents()
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

    /** 监听公开智能体列表，用于抽屉条目显示智能体头像 / 名称。 */
    private fun observeAgents() {
        viewModelScope.launch {
            homeRepository.getAgents().catch {
                Log.w(tag, "observeAgents: ${it.stackTraceToString()}")
            }.collect { response ->
                val agents = response.identities.map { it.toAgentUiModel() }
                _uiState.update { state ->
                    state.copy(
                        availableAgents = agents,
                        sessions = state.sessions.mergeAgentInfo(agents),
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
            // 刷新之前先重试上次远端失败的 pin / rename / delete，避免多端数据长期不一致。
            runCatching { homeRepository.retryPendingOperations() }
                .onFailure { Log.w(tag, "refreshRemoteSessions: retryPendingOperations failed", it) }
            homeRepository.getChats().catch {
                Log.w(tag, "refreshRemoteSessions: ${it.stackTraceToString()}")
            }.collect { response ->
                // 远端未携带 isPinned 时按本地为准，避免老版本服务端导致置顶丢失。
                val localPinnedIds = runCatching { homeRepository.getLocalPinnedSessionIds() }
                    .getOrDefault(emptySet())
                val agentMap = _uiState.value.availableAgents.associateBy { it.id }
                applySessionList(response.chats.map { it.toSessionUiModel(agentMap, localPinnedIds) })
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

        // 与 Room 的 ORDER BY isPinned DESC, updateTime DESC 保持一致，避免远端/本地混用时新会话掉到列表底部。
        val sortedSessions = sessions.sortedWith(
            compareByDescending<SessionUiModel> { it.isPinned }.thenByDescending { it.updatedAt },
        )

        _uiState.update { state ->
            state.copy(
                currentSessionId = nextSessionId,
                sessions = sortedSessions.map { it.copy(selected = it.id == nextSessionId) },
            ).withConversation(if (nextSessionId == null) emptyList() else state.messages)
        }

        if (nextSessionId == null) {
            _uiState.update { it.withConversation(emptyList()) }
            return
        }

        if (currentSessionId != nextSessionId) {
            loadSession(nextSessionId)
        }
    }

    /** 按当前登录态读取指定会话内容，并同步选中项、模型和消息列表。 */
    private fun loadSession(sessionId: String) {
        // 取消上一次未完成的加载，避免旧会话的响应覆盖新会话的消息列表。
        loadSessionJob?.cancel()
        // 加载期间也要取消进行中的标题生成，防止 prime 协程晚到时把 currentSessionId 写回新会话。
        generateTitleJob?.cancel()
        loadSessionJob = viewModelScope.launch {
            runCatching {
                homeRepository.loadStoredChat(
                    sessionId = sessionId,
                    useRemote = authRepository.session.value.isLoggedIn,
                )
            }.onSuccess { storedChat ->
                storedChat ?: return@onSuccess
                applyStoredChat(storedChat)
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(tag, "loadSession: sessionId=$sessionId", it)
            }
        }
    }

    /** 清空当前上下文，进入一条尚未发送的新会话。 */
    private fun startNewChat() {
        generateTitleJob?.cancel()
        _uiState.update { state ->
            state.copy(
                currentSessionId = null,
                currentSessionModelId = state.currentModel?.id,
                sessions = state.sessions.map { it.copy(selected = false) },
                isGeneratingTitle = false,
            ).withConversation(emptyList())
        }
    }

    /**
     * 把输入框中的文本转换成一条新用户消息，并启动一次完整对话流。
     *
     * 对新会话（`currentSessionId == null`）执行串行标题生成：
     * 先调用 [HomeRepository.generateTitle] 拿到 8~15 字标题，再落盘会话，
     * 然后才进入 LLM 流式回复。生成失败时 fallback 为「[Constants.DEFAULT_TITLE]」。
     */
    private fun sendNewMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGuestMessageLimitReached) return

        val currentModel = _uiState.value.currentModel ?: run {
            // 模型列表为空时（observeModels 失败 / 首屏冷启动）不静默 return，给用户一个明确反馈。
            _uiState.update { it.copy(errorMessage = "暂无可用模型，请稍后再试") }
            return
        }

        // 先把用户消息乐观写入本地状态；后续创建会话 / 编辑重生也会复用同一份 baseMessages。
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text,
        )
        val baseMessages = _uiState.value.messages + userMessage
        _uiState.update {
            it.copy(inputText = "").withConversation(baseMessages)
        }

        val currentSessionId = _uiState.value.currentSessionId
        if (currentSessionId == null) {
            primeNewChatAndContinue(text, currentModel, baseMessages)
        } else {
            sendConversation(baseMessages)
        }
    }

    /**
     * 串行流程：生成标题 → 创建会话 → 继续 LLM 对话。
     *
     * 仅在 `currentSessionId == null` 时被 [sendNewMessage] 调用；保留乐观写入的 `baseMessages` 不变。
     */
    private fun primeNewChatAndContinue(
        text: String,
        currentModel: ModelUiModel,
        baseMessages: List<ChatMessage>,
    ) {
        generateTitleJob?.cancel()
        generateTitleJob = viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingTitle = true) }
            val title = try {
                homeRepository.generateTitle(text, currentModel.id)
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isGeneratingTitle = false) }
                throw e
            } catch (e: Exception) {
                Log.w(tag, "sendNewMessage: generateTitle failed", e)
                Constants.DEFAULT_TITLE
            }
            _uiState.update { it.copy(isGeneratingTitle = false) }

            val useRemote = authRepository.session.value.isLoggedIn
            // 创建远端 / 本地会话（含首条用户消息），拿到 sessionId 再交给 sendConversation。
            val saved = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = null,
                    title = title,
                    modelId = currentModel.id,
                    messages = baseMessages,
                    useRemote = useRemote,
                )
            }.onFailure {
                Log.w(tag, "sendNewMessage: saveStoredChat failed", it)
            }.getOrNull()

            if (saved == null) {
                // 会话创建失败时不再继续走 LLM，避免 currentSessionId == null 时流式回复落不到正确位置。
                _uiState.update {
                    it.copy(errorMessage = "创建会话失败，请重试")
                }
                return@launch
            }
            // 只有当用户没在 prime 期间切换会话（即 currentSessionId 仍为 null）时，
            // 才把 saved.sessionId 写为 currentSessionId；用户在标题生成中选其它会话则保留其选择。
            if (_uiState.value.currentSessionId == null) {
                _uiState.update { it.copy(currentSessionId = saved.sessionId) }
            }
            sendConversation(baseMessages)
        }
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
        val sessionTitle = state.sessions.firstOrNull { it.id == state.currentSessionId }?.displayTitle
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

    /**
     * 切换会话置顶状态。
     *
     * 本地落盘由 Repository 内部完成（双写远端，失败不影响本地）。
     * UI 层用乐观更新立刻翻转 `isPinned`，让抽屉重新分组。
     */
    private fun setSessionPinned(sessionId: String, pin: Boolean) {
        val current = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        if (current.isPinned == pin) return
        _uiState.update { state ->
            state.copy(sessions = state.sessions.map { session ->
                if (session.id == sessionId) session.copy(isPinned = pin) else session
            })
        }
        viewModelScope.launch {
            runCatching {
                homeRepository.pinSession(
                    sessionId = sessionId,
                    isPinned = pin,
                    useRemote = authRepository.session.value.isLoggedIn,
                )
            }.onFailure {
                Log.w(tag, "setSessionPinned: sessionId=$sessionId pin=$pin", it)
            }
        }
    }

    /** 删除整条会话（含消息与级联收藏）。 */
    private fun deleteSession(sessionId: String) {
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.filterNot { it.id == sessionId },
                currentSessionId = if (state.currentSessionId == sessionId) null else state.currentSessionId,
            ).withConversation(if (state.currentSessionId == sessionId) emptyList() else state.messages)
        }
        viewModelScope.launch {
            runCatching {
                homeRepository.deleteSession(
                    sessionId = sessionId,
                    useRemote = authRepository.session.value.isLoggedIn,
                )
            }.onFailure {
                Log.w(tag, "deleteSession: sessionId=$sessionId", it)
            }
            // 删除当前会话后回退到列表第一条或留空。
            rebuildCurrentSessionAfterDeletion()
        }
    }

    /** 删除当前会话后挑选下一个落点：跳过置顶组，从普通组取最新一条，或回到「无选中」。 */
    private fun rebuildCurrentSessionAfterDeletion() {
        val state = _uiState.value
        if (state.currentSessionId != null) return
        // 优先选普通组最新一条；普通组为空时才退到置顶组，避免用户删除后跳到半年前置顶的旧会话。
        val next = state.sessions.firstOrNull { !it.isPinned } ?: state.sessions.firstOrNull()
        if (next == null) {
            _uiState.update { it.withConversation(emptyList()) }
            return
        }
        _uiState.update { it.copy(
            currentSessionId = next.id,
            sessions = it.sessions.map { session -> session.copy(selected = session.id == next.id) },
        ) }
        loadSession(next.id)
    }

    /** 重命名会话标题。空字符串直接拒绝，避免把会话改成空标题。 */
    private fun renameSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val current = _uiState.value.sessions.firstOrNull { it.id == sessionId } ?: return
        if (current.title == trimmed) return
        // 智能体会话不允许重命名（虽然 UI 已经屏蔽，这里再做一次兜底校验）。
        if (current.isAgent) return
        _uiState.update { state ->
            state.copy(sessions = state.sessions.map { session ->
                if (session.id == sessionId) session.copy(title = trimmed) else session
            })
        }
        viewModelScope.launch {
            runCatching {
                homeRepository.renameSession(
                    sessionId = sessionId,
                    newTitle = trimmed,
                    useRemote = authRepository.session.value.isLoggedIn,
                )
            }.onFailure {
                Log.w(tag, "renameSession: sessionId=$sessionId", it)
            }
        }
    }

    /** 将当前会话内容持久化到本地或远端，并回写仓库规范化后的会话状态。 */
    private fun persistConversation(messages: List<ChatMessage>) {
        val currentModel = _uiState.value.currentModel
        if (currentModel == null) {
            // 编辑/重生/删除消息这类「不需要 LLM」的场景下，即使没模型也要保留 UI 列表。
            _uiState.update {
                it.withConversation(messages).copy(errorMessage = "暂无可用模型，本次仅在本地展示")
            }
            return
        }

        val currentSessionId = _uiState.value.currentSessionId
        val useRemote = authRepository.session.value.isLoggedIn
        viewModelScope.launch {
            val storedChat = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = currentSessionId,
                    title = null,
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
        val currentModel = _uiState.value.currentModel ?: run {
            _uiState.update { it.copy(errorMessage = "暂无可用模型，请稍后再试") }
            return
        }
        val useRemote = authRepository.session.value.isLoggedIn

        // 同时只允许一个进行中的请求，避免新旧请求相互覆盖或重复落盘。
        sendJob?.cancel()

        sendJob = viewModelScope.launch {
            // 先保存用户侧消息，确保刷新或切后台后仍能恢复到“待回复”状态。
            val savedBaseChat = runCatching {
                homeRepository.saveStoredChat(
                    sessionId = _uiState.value.currentSessionId,
                    title = null,
                    modelId = currentModel.id,
                    messages = baseMessages,
                    useRemote = useRemote,
                )
            }.onFailure {
                Log.w(tag, "sendConversation: save base chat failed", it)
            }.getOrNull() ?: run {
                sendJob = null
                return@launch
            }

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

            // 标记进入请求中状态，UI 用来禁用发送按钮和展示状态提示。
            // 放在 try 内以确保异常路径下也能被 finally 复位（与 isResponding = false 配对）。
            try {
                _uiState.update { it.copy(isResponding = true) }

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
                        title = null,
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

                // 不管是流式结束、错误还是兜底收尾，结束前都要恢复 UI 状态。
            } catch (e: CancellationException) {
                // 用户主动停止：保留已累积的内容并落盘，避免刷新或切后台后丢失部分回复。
                if (assistantMessageId != null) {
                    isThinking = false
                    isGenerating = false
                    upsertAssistantMessage()
                    runCatching {
                        homeRepository.saveStoredChat(
                            sessionId = persistedSessionId,
                            title = null,
                            modelId = currentModel.id,
                            messages = conversation,
                            useRemote = useRemote,
                        )
                    }.onFailure {
                        Log.w(tag, "sendConversation: save cancelled chat failed", it)
                    }
                }
                throw e
            } finally {
                _uiState.update { it.copy(isResponding = false) }
                sendJob = null
            }
        }
    }

    /** 取消当前进行中的流式对话请求；用于输入区在等待响应时切换为停止按钮。 */
    private fun cancelMessage() {
        sendJob?.cancel()
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
    }

    private fun findModel(modelId: String): ModelUiModel? {
        return _uiState.value.availableModels.find { it.id == modelId || it.fullId == modelId }
    }

    private fun Session.toSessionUiModel(): SessionUiModel {
        return SessionUiModel(
            id = id,
            title = title,
            isPinned = isPinned,
            updatedAt = updateTime,
        )
    }

    /**
     * 把远端 chat DTO 转成 SessionUiModel。
     *
     * - [localPinnedIds]：本地已置顶的会话 ID 集合，用于「远端无字段时以本地为准」规则；
     * - [agentMap]：已缓存的智能体列表，用于合并 `chatTarget` → 智能体展示信息。
     */
    private fun ChatDto.toSessionUiModel(
        agentMap: Map<String, AgentUiModel>,
        localPinnedIds: Set<String>,
    ): SessionUiModel {
        val agentInfo = chatTarget?.id?.let { agentMap[it] }
        val effectivePinned = isPinned ?: (id in localPinnedIds)
        return SessionUiModel(
            id = id,
            title = title,
            isPinned = effectivePinned,
            agentId = chatTarget?.id,
            agentName = agentInfo?.name,
            updatedAt = updatedAt ?: createdAt ?: System.currentTimeMillis(),
        )
    }

    private fun AgentInfoDto.toAgentUiModel(): AgentUiModel = AgentUiModel(
        id = id,
        name = name,
    )

    /** 智能体列表变更后刷新已有会话条目的展示字段（名称）。 */
    private fun List<SessionUiModel>.mergeAgentInfo(agents: List<AgentUiModel>): List<SessionUiModel> {
        if (isEmpty() || agents.isEmpty()) return this
        val agentMap = agents.associateBy { it.id }
        // 守卫：智能体 ID 集合未变化时跳过全量重建，避免 observeAgents 重复触发时的不必要 copy。
        val existingIds = mapNotNull { it.agentId }.toSet()
        val newIds = agents.map { it.id }.toSet()
        if (existingIds.isNotEmpty() && existingIds == newIds) return this
        return map { session ->
            val agent = session.agentId?.let { agentMap[it] }
            if (agent == null) session else session.copy(agentName = agent.name)
        }
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
