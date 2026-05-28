package tech.xiaoniu.xnagent.data.repository

import tech.xiaoniu.xnagent.ui.model.ChatMessage

data class StoredChat(
    val sessionId: String,
    val title: String,
    val modelId: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
)