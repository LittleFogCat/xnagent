package tech.xiaoniu.xnagent.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
interface StreamChatApi {

    /** SSE-style chat endpoint — caller handles the response body stream */
    @POST("/api/chat")
    @Streaming
    suspend fun chat(@Body request: ChatRequest): ResponseBody
}
