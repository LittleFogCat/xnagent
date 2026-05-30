package tech.xiaoniu.xnagent.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import tech.xiaoniu.xnagent.data.remote.dto.AgentsResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatListResponse
import tech.xiaoniu.xnagent.data.remote.dto.ChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.ChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.CreateChatRequest
import tech.xiaoniu.xnagent.data.remote.dto.CurrentChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.DeleteChatResponse
import tech.xiaoniu.xnagent.data.remote.dto.ModelsResponse
import tech.xiaoniu.xnagent.data.remote.dto.UpdateChatRequest

/**
 * 普通聊天接口。
 *
 * 覆盖模型列表、智能体列表以及聊天记录的增删改查。
 */
interface ChatApi {

    /**
     * 发送一次非流式聊天请求。
     *
     * 当前仓库主要使用 [StreamChatApi] 走流式接口，这里保留给普通 HTTP 响应场景。
     */
    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): ResponseBody

    /** 获取可用模型列表及默认模型。 */
    @GET("/api/chat/models")
    suspend fun getModels(): ModelsResponse

    /** 获取公开智能体列表。 */
    @GET("/api/chat/agents")
    suspend fun getAgents(): AgentsResponse

    /** 获取当前用户的聊天记录摘要列表 */
    @GET("/api/chats")
    suspend fun getChats(): ChatListResponse

    /** 获取当前用户最近一条聊天记录，或按 id 查询指定记录 */
    @GET("/api/chats/current")
    suspend fun getCurrentChat(@Query("id") id: String? = null): CurrentChatResponse

    /** 按 ID 获取一条完整聊天记录 */
    @GET("/api/chats/{id}")
    suspend fun getChat(@Path("id") id: String): ChatResponse

    /** 创建一条新的聊天记录 */
    @POST("/api/chats")
    suspend fun createChat(@Body request: CreateChatRequest): ChatResponse

    /** 更新指定聊天记录 */
    @PUT("/api/chats/{id}")
    suspend fun updateChat(
        @Path("id") id: String,
        @Body request: UpdateChatRequest
    ): ChatResponse

    /** 删除当前用户的一条聊天记录 */
    @DELETE("/api/chats/{id}")
    suspend fun deleteChat(@Path("id") id: String): DeleteChatResponse
}
