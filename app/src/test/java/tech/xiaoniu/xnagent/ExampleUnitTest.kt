package tech.xiaoniu.xnagent

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import tech.xiaoniu.xnagent.data.remote.dto.ChatMessageDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun chatRequest_serialization_keepsThinkingAndStreamingDefaults() {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val request = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessageDto(role = "user", content = "hello"))
        )

        val encoded = json.encodeToString(ChatRequest.serializer(), request)

        assertTrue(encoded.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(encoded.contains("\"streaming\":true"))
    }
}