package tech.xiaoniu.xnagent.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

@Serializable
data class ModelConfig(
    val providers: Map<String, ModelProvider>,
    val default: String?,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun fromJson(jsonString: String): ModelConfig? {
            return runCatching {
                json.decodeFromString<ModelConfig>(jsonString)
            }.getOrNull()
        }
    }

    fun getDefaultModel(): Model? {
        val value = default ?: return null
        val splitIndex = value.indexOf('/')
        if (splitIndex <= 0 || splitIndex == value.lastIndex) return null
        val providerName = value.substring(0, splitIndex)
        val modelName = value.substring(splitIndex + 1)
        val provider = providers[providerName]
        return provider?.getModel(modelName)
    }
}

/** Entity for LLM model provider **/
@Serializable
data class ModelProvider(
    val name: String,
    val baseUrl: String,
    val models: List<Model>,
    val api: ModelProviderApi = ModelProviderApi.OpenAI,
) {
    fun getModel(name: String): Model? {
        return models.find { it.name == name || it.id == name }
    }

    fun getChatEndpoint(): String {
        return baseUrl + api.chatPath
    }
}
@Serializable(with = ModelProviderApiSerializer::class)
sealed class ModelProviderApi(
    val name: String,
    /** Should start with '/' **/
    val chatPath: String
) {
    object OpenAI : ModelProviderApi(
        name = "openai-completions",
        chatPath = "/chat/completions"
    )
}

object ModelProviderApiSerializer : kotlinx.serialization.KSerializer<ModelProviderApi> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
        get() = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "ModelProviderApi",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING
        )

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ModelProviderApi {
        val name = decoder.decodeString()
        return when (name) {
            ModelProviderApi.OpenAI.name -> ModelProviderApi.OpenAI
            else -> ModelProviderApi.OpenAI
        }
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ModelProviderApi) {
        encoder.encodeString(value.name)
    }
}

/** Entity for LLM model **/
@Serializable
data class Model(
    val id: String,
    val name: String,
)