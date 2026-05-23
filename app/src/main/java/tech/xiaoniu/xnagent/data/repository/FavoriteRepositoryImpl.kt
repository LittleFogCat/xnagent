package tech.xiaoniu.xnagent.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : FavoriteRepository {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(loadFavorites())

    override val favorites: StateFlow<List<FavoriteMessage>> = _favorites.asStateFlow()

    override suspend fun addFavorite(message: FavoriteMessage) {
        val updated = listOf(message) + _favorites.value.filterNot { it.id == message.id }
        persist(updated)
    }

    override suspend fun removeFavorite(id: String) {
        persist(_favorites.value.filterNot { it.id == id })
    }

    override suspend fun clearFavorites() {
        persist(emptyList())
    }

    private fun loadFavorites(): List<FavoriteMessage> {
        val raw = preferences.getString(KEY_FAVORITES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FavoriteMessage>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun persist(items: List<FavoriteMessage>) {
        preferences.edit()
            .putString(
                KEY_FAVORITES,
                json.encodeToString(ListSerializer(FavoriteMessage.serializer()), items)
            )
            .apply()
        _favorites.value = items
    }

    private companion object {
        const val PREF_NAME = "favorite_store"
        const val KEY_FAVORITES = "favorites"
    }
}