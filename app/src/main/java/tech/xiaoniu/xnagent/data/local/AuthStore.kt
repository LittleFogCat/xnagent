package tech.xiaoniu.xnagent.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.xiaoniu.xnagent.data.repository.AuthSession
import tech.xiaoniu.xnagent.data.repository.AuthUser
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
    private val _session = MutableStateFlow(loadSession())

    val session: StateFlow<AuthSession> = _session.asStateFlow()

    fun currentToken(): String? {
        val current = _session.value
        return current.token?.takeIf { current.isLoggedIn }
    }

    fun saveSession(session: AuthSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USERNAME, session.user?.username)
            .putString(KEY_EMAIL, session.user?.email)
            .putBoolean(KEY_IS_GUEST, session.isGuest)
            .apply()
        _session.value = session
    }

    fun clear() {
        preferences.edit().clear().apply()
        _session.value = AuthSession()
    }

    private fun loadSession(): AuthSession {
        val token = preferences.getString(KEY_TOKEN, null)
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
            user = user,
            isGuest = isGuest && user == null && token.isNullOrBlank(),
        )
    }

    private companion object {
        const val PREF_NAME = "auth_store"
        const val KEY_TOKEN = "token"
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_IS_GUEST = "is_guest"
    }
}