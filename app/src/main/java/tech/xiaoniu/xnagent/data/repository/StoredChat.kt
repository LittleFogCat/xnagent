package tech.xiaoniu.xnagent.data.repository

import tech.xiaoniu.xnagent.ui.model.ChatMessage

/**
 * 仓库层统一使用的会话快照。
 *
 * 无论来源于本地数据库还是远端接口，都会先收敛到这个结构，再交给 ViewModel 使用。
 */
data class StoredChat(
    val sessionId: String,
    val title: String,
    val modelId: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
)