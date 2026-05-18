package tech.xiaoniu.xnagent.ui.model

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
sealed class SendToLLMResult {
    data class Thinking(val content: String) : SendToLLMResult()
    data class Streaming(val content: String) : SendToLLMResult()
    data class Success(val response: String) : SendToLLMResult()
    data class Error(val error: Throwable) : SendToLLMResult()
}