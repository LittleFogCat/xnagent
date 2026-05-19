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

interface FavoriteRepository {
    val favorites: StateFlow<List<FavoriteMessage>>

    suspend fun addFavorite(message: FavoriteMessage)

    suspend fun removeFavorite(id: String)
}