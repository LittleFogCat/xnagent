package tech.xiaoniu.xnagent.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest

/**
 * SSE 聊天接口。
 *
 * 调用方直接消费原始响应流，再按 data 行解析为 reasoning/content 片段。
 */
interface StreamChatApi {

    /**
     * 发起流式聊天请求。
     *
     * @param request 聊天请求体。
     */
    @POST("/api/chat")
    @Streaming
    suspend fun chat(@Body request: ChatRequest): ResponseBody
}
