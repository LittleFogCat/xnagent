package tech.xiaoniu.xnagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 本地表实体。
 */

/**
 * 会话表。
 *
 * 只保存抽屉和恢复会话所需的摘要信息，消息正文单独存放在 chat_message 表。
 */
@Entity(tableName = "session")
data class Session(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String,
    val createTime: Long,
    val updateTime: Long,
)

/**
 * 消息表。
 *
 * 以 sessionId 关联会话，按 createTime 正序还原聊天上下文。
 */
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