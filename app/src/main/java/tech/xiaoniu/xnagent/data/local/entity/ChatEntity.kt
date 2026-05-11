package tech.xiaoniu.xnagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

/** 一次会话 **/
@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: String,
    val title: String,
    val createTime: Long
)

/** 一条聊天记录 **/
@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createTime: Long
)