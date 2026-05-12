package tech.xiaoniu.xnagent.data.mock

import okhttp3.ResponseBody
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.dto.AgentInfoDto
import tech.xiaoniu.xnagent.data.remote.dto.AgentsResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatListResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatMessageDto
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.CurrentChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.DeleteChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.ModelInfoDto
import tech.xiaoniu.xnagent.data.remote.dto.ModelsResponse
import tech.xiaoniu.xnagent.data.remote.dto.UpdateChatRequest

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
object ChatApiMock : ChatApi {

    val models = listOf(
        ModelInfoDto(
            "longcat/LongCat-Flash-Chat",
            "LongCat-Flash-Chat",
            "longcat"
        )
    )
    val agents = listOf(AgentInfoDto("123", "Alice", "assistant", "A helpful assistant", null, true))

    val chats = listOf(
        ChatDto(
            "1",
            "user123",
            "Chat with LongCat",
            "longcat/LongCat-Flash-Chat",
            null,
            listOf(
                ChatMessageDto("user", "Hello, LongCat!"),
                ChatMessageDto("assistant", "Meow! How can I help you today?")
            ),
            1712137600000,
            1712137600000
        )
    )

    override suspend fun chat(request: ChatRequest): ResponseBody {
        TODO("Not yet implemented")
    }

    override suspend fun getModels(): ModelsResponse {
        return ModelsResponse(models)
    }

    override suspend fun getAgents(): AgentsResponse {
        return AgentsResponse(agents)
    }

    override suspend fun getChats(): ChatListResponse {
        return ChatListResponse(chats)
    }

    override suspend fun getCurrentChat(id: String?): CurrentChatResponse {
        val chat = chats.firstOrNull { it.id == id } ?: chats.firstOrNull()
        return CurrentChatResponse(chat)
    }

    override suspend fun getChat(id: String): ChatResponse {
        val chat = chats.firstOrNull { it.id == id }
        return if (chat != null) {
            ChatResponse(chat)
        } else {
            throw IllegalArgumentException("Chat with id $id not found")
        }
    }

    override suspend fun createChat(request: CreateChatRequest): ChatResponse {
        TODO("Not yet implemented")
    }

    override suspend fun updateChat(
        id: String,
        request: UpdateChatRequest
    ): ChatResponse {
        TODO("Not yet implemented")
    }

    override suspend fun deleteChat(id: String): DeleteChatResponse {
        TODO("Not yet implemented")
    }
}