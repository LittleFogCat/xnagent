package tech.xiaoniu.xnagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

/** 一次会话 **/
@Entity(tableName = "session")
data class Session(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String,
    val createTime: Long,
    val updateTime: Long,
)

/** 一条聊天记录 **/
@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val reasoningContent: String = "",
    val reasoningDurationMs: Long? = null,
    val createTime: Long,
)