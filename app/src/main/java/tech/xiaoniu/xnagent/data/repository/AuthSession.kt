package tech.xiaoniu.xnagent.data.repository

/**
 * 当前认证用户。
 */
data class AuthUser(
    val username: String,
    val email: String,
)

/**
 * 应用当前认证状态。
 */
data class AuthSession(
    val token: String? = null,
    val user: AuthUser? = null,
    val isGuest: Boolean = false,
) {
    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank() && user != null

    val canEnterHome: Boolean
        get() = isGuest || isLoggedIn
}