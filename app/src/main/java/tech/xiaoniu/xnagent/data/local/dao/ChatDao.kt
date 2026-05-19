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
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM session ORDER BY updateTime DESC")
    fun querySessionList(): Flow<List<Session>>

    @Query("SELECT * FROM session ORDER BY updateTime DESC")
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

    @Query("DELETE FROM chat_message WHERE sessionId = :sessionId")
    suspend fun deleteChatMessagesBySessionId(sessionId: String)

    @Query("DELETE FROM session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_message")
    suspend fun clearChatMessages()

    @Query("DELETE FROM session")
    suspend fun clearSessions()

    @Transaction
    suspend fun replaceSessionMessages(session: Session, messages: List<ChatMessage>) {
        upsertSession(session)
        deleteChatMessagesBySessionId(session.id)
        if (messages.isNotEmpty()) {
            insertChatMessages(messages)
        }
    }
}