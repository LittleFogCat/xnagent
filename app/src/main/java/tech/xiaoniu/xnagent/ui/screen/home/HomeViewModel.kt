package tech.xiaoniu.xnagent.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: Flow<HomeUiState> = _uiState

    fun dispatch(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Initialize -> {
                viewModelScope.launch {
                    homeRepository.loadModelConfig().collect {
                        val modelUiModels = it?.providers?.values?.flatMap { provider ->
                            provider.models.map { model ->
                                ModelUiModel(model.id, model.name, provider.name)
                            }
                        }
                        if (modelUiModels.isNullOrEmpty()) {
                            return@collect
                        }
                        _uiState.value = _uiState.value.copy(
                            availableModels = modelUiModels,
                            currentModel = modelUiModels[0]
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
                val text = _uiState.value.inputText.trim()
                if (text.isEmpty()) return

                val newMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.USER,
                    content = text
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + newMessage,
                    inputText = ""
                )

                viewModelScope.launch {
                    homeRepository.sendToLLM(newMessage.toLLMMessage()).collect {
                        when (it) {
                            is SendToLLMResult.Success -> {
                                val response = it.response
                                val newMessage = ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    role = MessageRole.ASSISTANT,
                                    content = response
                                )
                                _uiState.value = _uiState.value.copy(
                                    messages = _uiState.value.messages + newMessage
                                )
                            }

                            is SendToLLMResult.Error -> {
                                val response = "哎呀，好像出错了！错误信息：${it.error.message}"
                                val newMessage = ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    role = MessageRole.ASSISTANT,
                                    content = response
                                )
                                _uiState.value = _uiState.value.copy(
                                    messages = _uiState.value.messages + newMessage
                                )
                            }
                        }
                    }
                }
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

}
