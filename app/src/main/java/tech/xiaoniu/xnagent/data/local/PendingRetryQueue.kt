package tech.xiaoniu.xnagent.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待重试的远端会话写操作。
 *
 * 当 [tech.xiaoniu.xnagent.data.repository.HomeRepositoryImpl] 的 pinSession / renameSession /
 * deleteSession 在远端失败时入队；下次 refreshRemoteSessions 之前由 ViewModel 触发 retry，逐条重试并清队。
 */
@Serializable
sealed interface PendingRetryOp {
    @Serializable
    data class Pin(val sessionId: String, val isPinned: Boolean) : PendingRetryOp

    @Serializable
    data class Rename(val sessionId: String, val newTitle: String) : PendingRetryOp

    @Serializable
    data class Delete(val sessionId: String) : PendingRetryOp
}

/**
 * 持久化的待重试队列。
 *
 * 用 SharedPreferences + JSON 序列化保证应用重启后队列不丢；同一条 op 不会重复入队。
 */
@Singleton
class PendingRetryQueue @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _operations = MutableStateFlow(load())

    /** 当前待重试操作列表；UI 与 ViewModel 据此观察。 */
    val operations: StateFlow<List<PendingRetryOp>> = _operations.asStateFlow()

    /** 入队；同 op（按 equals）只保留一条。 */
    fun add(op: PendingRetryOp) {
        val current = _operations.value
        if (current.contains(op)) return
        persist(current + op)
    }

    /** 出队；操作成功后由调用方移除。 */
    fun remove(op: PendingRetryOp) {
        persist(_operations.value.filterNot { it == op })
    }

    /** 清空；用于「退出登录 / 清除本地数据」等场景。 */
    fun clear() {
        persist(emptyList())
    }

    private fun load(): List<PendingRetryOp> {
        val raw = prefs.getString(KEY_OPERATIONS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PendingRetryOp>>(raw)
        }.getOrElse {
            Log.w(TAG, "load: decode failed, dropping queue", it)
            emptyList()
        }
    }

    private fun persist(ops: List<PendingRetryOp>) {
        _operations.value = ops
        prefs.edit()
            .putString(
                KEY_OPERATIONS,
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(PendingRetryOp.serializer()),
                    ops,
                ),
            )
            .apply()
    }

    private companion object {
        const val TAG = "PendingRetryQueue"
        const val PREF_NAME = "pending_retry"
        const val KEY_OPERATIONS = "operations"
    }
}