package tech.xiaoniu.xnagent.data.local

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tech.xiaoniu.xnagent.data.local.network.NetworkConfig
import tech.xiaoniu.xnagent.data.remote.dto.RefreshRequest
import tech.xiaoniu.xnagent.data.remote.dto.RefreshResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * 负责在 OkHttp 拦截器中同步刷新 access token。
 *
 * 使用独立 OkHttpClient（无鉴权拦截器）避免循环依赖；
 * ReentrantLock + Condition 保证同一时刻只发一个刷新请求。
 */
@Singleton
class TokenRefreshHandler @Inject constructor(
    private val authStore: AuthStore,
    private val json: Json,
) {
    /** 独立客户端，不附加任何鉴权或日志拦截器，避免无限递归。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    @Volatile
    private var isRefreshing = false
    @Volatile
    private var lastRefreshSuccess = false

    /** 同步执行 token 刷新，返回是否成功。若已有刷新进行中则等待其结果。 */
    fun refreshToken(): Boolean {
        lock.withLock {
            if (isRefreshing) {
                // 已有刷新进行中，等待其完成
                condition.await(10, TimeUnit.SECONDS)
                return lastRefreshSuccess
            }
            isRefreshing = true
            lastRefreshSuccess = false
        }

        return try {
            val currentRefreshToken = readRefreshToken() ?: return false
            val deviceId = authStore.getDeviceId()

            val requestBody = json.encodeToString(
                RefreshRequest.serializer(),
                RefreshRequest(refreshToken = currentRefreshToken, deviceId = deviceId),
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${NetworkConfig.BASE_URL}api/refresh")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val refreshResponse = json.decodeFromString(RefreshResponse.serializer(), body)
                    authStore.updateTokens(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken,
                    )
                    lastRefreshSuccess = true
                }
            }
            lastRefreshSuccess
        } catch (_: Exception) {
            false
        } finally {
            lock.withLock {
                isRefreshing = false
                condition.signalAll()
            }
        }
    }

    /** 登出时唤醒等待中的刷新线程并重置状态。 */
    fun reset() {
        lock.withLock {
            isRefreshing = false
            lastRefreshSuccess = false
            condition.signalAll()
        }
    }

    private fun readRefreshToken(): String? {
        return authStore.session.value.refreshToken?.takeIf { it.isNotBlank() }
    }
}
