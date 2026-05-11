package tech.xiaoniu.xnagent.ui.screen.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tech.xiaoniu.xnagent.data.LLMMessage
import javax.inject.Inject
import tech.xiaoniu.xnagent.data.ModelConfig
import tech.xiaoniu.xnagent.ui.model.SendToLLMResult

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
class HomeRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : HomeRepository {
    private val appContext: Context = context
    private val llmMessages: MutableList<LLMMessage> = mutableListOf()
//
//    override val modelConfigFlow: Flow<ModelConfig?> = flow {
//        emit(loadModelConfig())
//    }.catch {
//        emit(null)
//    }.stateIn(
//        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
//        started = SharingStarted.Lazily,
//        initialValue = null
//    )

    override fun loadModelConfig() = flow {
        val modelConfig = appContext.assets
            .open("model_config.json")
            .bufferedReader()
            .use { reader ->
                ModelConfig.fromJson(reader.readText())
            }
        emit(modelConfig)
    }

    override fun sendToLLM(message: LLMMessage): Flow<SendToLLMResult> = flow {
        // todo
    }
//
//    fun getAvailableModels(): List<ModelUiModel> = availableModelsCache ?: loadAvailableModels().also {
//        availableModelsCache = it
//    }

//    private fun loadAvailableModels(): List<ModelUiModel> {
//        val config = runCatching {
//            appContext.assets
//                .open("model_config.json")
//                .bufferedReader()
//                .use { reader ->
//                    ModelConfig.fromJson(reader.readText())
//                }
//        }.getOrNull()
//
//        return config?.providers?.values?.flatMap { provider ->
//            provider.models.map { model ->
//                ModelUiModel(model.id, model.name, provider.name)
//            }
//        } ?: emptyList()
//    }

}