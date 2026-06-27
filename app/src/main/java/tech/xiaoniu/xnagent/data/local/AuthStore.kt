package tech.xiaoniu.xnagent.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.xiaoniu.xnagent.data.repository.AuthSession
import tech.xiaoniu.xnagent.data.repository.AuthUser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地持久化认证状态。
 */
@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 启动时从 SharedPreferences 恢复认证态，并持续作为内存中的单一真相源。 */
    private val _session = MutableStateFlow(loadSession())

    /** 提供给仓库和根 ViewModel 观察的认证状态流。 */
    val session: StateFlow<AuthSession> = _session.asStateFlow()

    /** 返回当前可用于鉴权请求的 token；游客态和未登录态都会返回空。 */
    fun currentToken(): String? {
        val current = _session.value
        return current.token?.takeIf { current.isLoggedIn }
    }

    /** 获取设备唯一标识，首次调用时生成 UUID 并持久化。 */
    fun getDeviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    /** 仅更新 access token 和 refresh token，保留用户身份等其他字段不变。供拦截器线程安全调用。 */
    fun updateTokens(accessToken: String, refreshToken: String) {
        val current = _session.value
        val updated = current.copy(token = accessToken, refreshToken = refreshToken)
        preferences.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        _session.value = updated
    }

    /** 持久化会话并立即更新内存态，保证界面和存储同步。 */
    fun saveSession(session: AuthSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USERNAME, session.user?.username)
            .putString(KEY_EMAIL, session.user?.email)
            .putBoolean(KEY_IS_GUEST, session.isGuest)
            .apply()
        _session.value = session
    }

    /** 清空本地认证数据并重置为未登录状态。 */
    fun clear() {
        preferences.edit().clear().apply()
        _session.value = AuthSession()
    }

    /** 从 SharedPreferences 恢复会话；只有同时缺少 token 和用户信息时才视作游客态。 */
    private fun loadSession(): AuthSession {
        val token = preferences.getString(KEY_TOKEN, null)
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        val username = preferences.getString(KEY_USERNAME, null)
        val email = preferences.getString(KEY_EMAIL, null)
        val isGuest = preferences.getBoolean(KEY_IS_GUEST, false)
        val user = if (!username.isNullOrBlank() && !email.isNullOrBlank()) {
            AuthUser(username = username, email = email)
        } else {
            null
        }
        return AuthSession(
            token = token,
            refreshToken = refreshToken,
            user = user,
            isGuest = isGuest && user == null && token.isNullOrBlank(),
        )
    }

    private companion object {
        /** SharedPreferences 文件名。 */
        const val PREF_NAME = "auth_store"

        /** 登录 token 键。 */
        const val KEY_TOKEN = "token"

        /** refresh token 键。 */
        const val KEY_REFRESH_TOKEN = "refresh_token"

        /** 设备唯一标识键。 */
        const val KEY_DEVICE_ID = "device_id"

        /** 用户名键。 */
        const val KEY_USERNAME = "username"

        /** 邮箱键。 */
        const val KEY_EMAIL = "email"

        /** 游客态标记键。 */
        const val KEY_IS_GUEST = "is_guest"
    }
}