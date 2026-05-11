package tech.xiaoniu.xnagent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tech.xiaoniu.xnagent.data.entity.ChatMessage
import tech.xiaoniu.xnagent.data.entity.Session

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM session")
    fun querySessionList(): Flow<List<Session>>

    @Query("SELECT * FROM chat_message")
    fun queryChatMessageList(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId")
    fun queryChatMessagesBySessionId(sessionId: Long): Flow<List<ChatMessage>>

    @Insert
    fun insertChatMessage(message: ChatMessage): Long
}