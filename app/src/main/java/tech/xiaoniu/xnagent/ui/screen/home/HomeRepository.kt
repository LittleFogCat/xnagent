package tech.xiaoniu.xnagent.ui.screen.home

import kotlinx.coroutines.flow.Flow
import tech.xiaoniu.xnagent.data.LLMMessage
import tech.xiaoniu.xnagent.data.ModelConfig
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
interface HomeRepository {
//    val modelConfigFlow: Flow<ModelConfig?>
    fun loadModelConfig(): Flow<ModelConfig?>
    fun sendToLLM(message: LLMMessage): Flow<SendToLLMResult>
}