package tech.xiaoniu.xnagent.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.common.util.currentTimeF
import tech.xiaoniu.xnagent.data.local.entity.Session
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.HomeUiState
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.ModelUiModel
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult
import tech.xiaoniu.xnagent.ui.model.toLLMMessage
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
                    homeRepository.getModels().collect { response ->
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

            homeRepository.sendToLLM(request).collect { result ->
                when (result) {
                    is SendToLLMResult.Streaming -> {
                        accumulatedContent += result.content
                        Log.e(TAG, "sendToLLM: ${currentTimeF()} streaming...: ${result.content}")
                        val currentMessages = _uiState.value.messages.toMutableList()
                        if (assistantMessageId == null) {
                            assistantMessageId = UUID.randomUUID().toString()
                            currentMessages.add(
                                ChatMessage(
                                    id = assistantMessageId,
                                    role = MessageRole.ASSISTANT,
                                    content = accumulatedContent
                                )
                            )
                        } else {
                            val index = currentMessages.indexOfFirst { it.id == assistantMessageId }
                            if (index >= 0) {
                                currentMessages[index] = currentMessages[index].copy(
                                    content = accumulatedContent
                                )
                            }
                        }
                        _uiState.value = _uiState.value.copy(messages = currentMessages)
                    }

                    is SendToLLMResult.Error -> {
                        Log.w(TAG, "sendToLLM: response error: ${result.error.stackTraceToString()}")
                        val response = "哎呀，好像出错了！错误信息：${result.error.message}"
                        val errorMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = response
                        )
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + errorMessage
                        )
                    }

                    is SendToLLMResult.Success -> {
                        Log.i(TAG, "sendToLLM: onSuccess")
                    }
                }
            }
        }
    }

}
