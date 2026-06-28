package tech.xiaoniu.xnagent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tech.xiaoniu.xnagent.data.local.entity.ChatMessage
import tech.xiaoniu.xnagent.data.local.entity.Session

/**
 * 本地聊天数据访问接口。
 *
 * 负责会话列表查询、消息读写，以及“整会话替换”这类事务操作。
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM session ORDER BY isPinned DESC, updateTime DESC")
    fun querySessionList(): Flow<List<Session>>

    @Query("SELECT * FROM session ORDER BY isPinned DESC, updateTime DESC")
    suspend fun getSessionList(): List<Session>

    @Query("SELECT * FROM session WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): Session?

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY createTime ASC")
    fun queryChatMessagesBySessionId(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY createTime ASC")
    suspend fun getChatMessagesBySessionId(sessionId: String): List<ChatMessage>

    @Upsert
    suspend fun upsertSession(session: Session)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatMessage>)

    @Query("UPDATE session SET isPinned = :isPinned WHERE id = :sessionId")
    suspend fun updateSessionPinned(sessionId: String, isPinned: Boolean)

    @Query("UPDATE session SET title = :title, updateTime = :updateTime WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updateTime: Long)

    @Query("DELETE FROM chat_message WHERE sessionId = :sessionId")
    suspend fun deleteChatMessagesBySessionId(sessionId: String)

    @Query("DELETE FROM session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_message")
    suspend fun clearChatMessages()

    @Query("DELETE FROM session")
    suspend fun clearSessions()

    /**
     * 事务性地删除指定会话及其全部消息。
     *
     * 区别于 [deleteSession] 单独删表，避免出现孤儿消息。
     */
    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteChatMessagesBySessionId(sessionId)
        deleteSession(sessionId)
    }

    /**
     * 事务性地重写某个会话及其全部消息。
     *
     * 适用于消息编辑、重新生成回答等需要“截断后整体回写”的场景。
     */
    @Transaction
    suspend fun replaceSessionMessages(session: Session, messages: List<ChatMessage>) {
        upsertSession(session)
        deleteChatMessagesBySessionId(session.id)
        if (messages.isNotEmpty()) {
            insertChatMessages(messages)
        }
    }
}