package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteMessage(
    val id: String,
    val sessionId: String?,
    val sessionTitle: String,
    val role: String,
    val content: String,
    val timestamp: Long,
)

/**
 * 收藏消息仓库。
 *
 * 负责维护聊天收藏列表，并供聊天页与设置页统一读写。
 */
interface FavoriteRepository {
    /** 当前全部收藏消息。 */
    val favorites: StateFlow<List<FavoriteMessage>>

    /**
     * 新增一条收藏消息。
     *
     * @param message 需要加入收藏列表的消息快照。
     */
    suspend fun addFavorite(message: FavoriteMessage)

    /**
     * 删除一条收藏消息。
     *
     * @param id 收藏消息 ID。
     */
    suspend fun removeFavorite(id: String)

    /** 清空全部收藏消息。 */
    suspend fun clearFavorites()
}