package tech.xiaoniu.xnagent.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.HomeUiState
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.ModelUiModel
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult
import tech.xiaoniu.xnagent.ui.model.SessionUiModel
import java.util.UUID

/**
 * 简单的 UI 状态 ViewModel：保存 agent 模式 / 输入文本 / 消息列表
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository
) : ViewModel() {
    private val TAG = javaClass.simpleName

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: Flow<HomeUiState> = _uiState

    val sessions: Flow<List<Session>> = homeRepository.sessions

    fun dispatch(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Initialize -> {
                viewModelScope.launch {
                    homeRepository.getModels().catch {
                        Log.w(TAG, "dispatch: getModels error: ${it.stackTraceToString()}")
                    }.collect { response ->
                        val modelUiModels = response.models.map { model ->
                            ModelUiModel(model.id, model.name, model.provider)
                        }
                        if (modelUiModels.isEmpty()) {
                            return@collect
                        }
                        val defaultModel = response.defaultModel?.let { defaultId ->
                            modelUiModels.find { it.id == defaultId || it.fullId == defaultId }
                        } ?: modelUiModels[0]
                        _uiState.value = _uiState.value.copy(
                            availableModels = modelUiModels,
                            currentModel = defaultModel
                        )
                    }
                    homeRepository.getChats().catch {
                        Log.w(TAG, "dispatch: getChats error: ${it.stackTraceToString()}")
                    }.collect { chats ->
                        _uiState.value = _uiState.value.copy(sessions = chats.chats.map {
                            SessionUiModel(
                                id = it.id,
                                title = it.title,
                                lastMessageTime = it.updatedAt ?: System.currentTimeMillis()
                            )
                        })
                    }
                }
            }

            is HomeIntent.SetAgentMode -> {
                _uiState.value = _uiState.value.copy(agentMode = intent.mode)
            }

            is HomeIntent.UpdateInput -> {
                _uiState.value = _uiState.value.copy(inputText = intent.text)
            }

            HomeIntent.SendMessage -> {
                sendToLLM()
            }

            is HomeIntent.AppendAssistantMessage -> {
                val newMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = intent.content
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + newMessage
                )
            }

            is HomeIntent.SelectModel -> {
                _uiState.value = _uiState.value.copy(currentModel = intent.model)
            }
        }
    }

    private fun sendToLLM() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        val currentModel = _uiState.value.currentModel ?: return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text
        )

        // 构建请求消息列表：已有消息 + 新用户消息
        val allMessages = _uiState.value.messages + userMessage

        _uiState.value = _uiState.value.copy(
            messages = allMessages,
            inputText = ""
        )

        val request = ChatRequest(
            model = currentModel.id,
            messages = allMessages.map { it.toChatMessageDto() }
        )

        viewModelScope.launch {
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
                    isThinking = isThinking
                )
            }

            fun upsertAssistantMessage() {
                val currentMessages = _uiState.value.messages.toMutableList()
                if (assistantMessageId == null) {
                    assistantMessageId = UUID.randomUUID().toString()
                }

                val assistantMessage = buildAssistantMessage().copy(id = assistantMessageId!!)
                val index = currentMessages.indexOfFirst { it.id == assistantMessageId }
                if (index >= 0) {
                    currentMessages[index] = assistantMessage
                } else {
                    currentMessages.add(assistantMessage)
                }
                _uiState.value = _uiState.value.copy(messages = currentMessages)
            }

            homeRepository.sendToLLM(request).collect { result ->
                when (result) {
                    is SendToLLMResult.Thinking -> {
                        Log.d(TAG, "sendToLLM: ${currentTimeF()} thinking...: ${result.content}")
                        if (thinkingStartedAtMs == null) {
                            thinkingStartedAtMs = System.currentTimeMillis()
                        }
                        isThinking = true
                        accumulatedReasoning += result.content
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Streaming -> {
                        accumulatedContent += result.content
                        isThinking = false
                        Log.e(TAG, "sendToLLM: ${currentTimeF()} streaming...: ${result.content}")
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Error -> {
                        Log.w(TAG, "sendToLLM: response error: ${result.error.stackTraceToString()}")
                        val response = "哎呀，好像出错了！错误信息：${result.error.message}"
                        accumulatedContent = response
                        isThinking = false
                        upsertAssistantMessage()
                    }

                    is SendToLLMResult.Success -> {
                        isThinking = false
                        Log.i(TAG, "sendToLLM: onSuccess")
                    }
                }
            }

            if (assistantMessageId != null && accumulatedReasoning.isNotBlank() && isThinking) {
                isThinking = false
                upsertAssistantMessage()
            }
        }
    }

}
