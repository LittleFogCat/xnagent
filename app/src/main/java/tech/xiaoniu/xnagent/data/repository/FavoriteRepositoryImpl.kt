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

    /** 启动时一次性从 SharedPreferences 读取收藏，并以 StateFlow 形式暴露给 UI。 */
    private val _favorites = MutableStateFlow(loadFavorites())

    override val favorites: StateFlow<List<FavoriteMessage>> = _favorites.asStateFlow()

    override suspend fun addFavorite(message: FavoriteMessage) {
        // 新收藏始终插到列表最前，同时去重，保证设置页按最近收藏顺序展示。
        val updated = listOf(message) + _favorites.value.filterNot { it.id == message.id }
        persist(updated)
    }

    override suspend fun removeFavorite(id: String) {
        persist(_favorites.value.filterNot { it.id == id })
    }

    override suspend fun clearFavorites() {
        persist(emptyList())
    }

    /** 从 SharedPreferences 反序列化收藏列表，异常时回退为空集合。 */
    private fun loadFavorites(): List<FavoriteMessage> {
        val raw = preferences.getString(KEY_FAVORITES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FavoriteMessage>>(raw)
        }.getOrDefault(emptyList())
    }

    /** 统一写回 SharedPreferences，并同步更新内存态。 */
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
        /** SharedPreferences 文件名。 */
        const val PREF_NAME = "favorite_store"

        /** 收藏列表的 JSON 存储键。 */
        const val KEY_FAVORITES = "favorites"
    }
}