package tech.xiaoniu.xnagent.ui.model

/**
 * 流式聊天结果。
 *
 * SSE 解析后会被归一为“推理中”“正文流”“完成”“错误”四种事件。
 */
sealed class SendToLLMResult {
    data class Thinking(val content: String) : SendToLLMResult()
    data class Streaming(val content: String) : SendToLLMResult()
    data class Success(val response: String) : SendToLLMResult()
    data class Error(val error: Throwable) : SendToLLMResult()
}