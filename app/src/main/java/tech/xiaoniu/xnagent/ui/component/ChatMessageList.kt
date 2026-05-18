package tech.xiaoniu.xnagent.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import tech.xiaoniu.xnagent.R
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.MessageRole

/**
 * 单条聊天消息项
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val shouldShowReasoning = message.isThinking || reasoningExpanded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // 助手头像
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Assistant",
                    modifier = Modifier
                        .padding(4.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        BoxWithConstraints {
            Box(
                modifier = Modifier
                    .widthIn(max = if (isUser) maxWidth * 0.77f else maxWidth)
                    .background(
                        color = if (isUser) {
                            colorResource(R.color.chat_bubble_bg_user)
                        } else {
                            colorResource(R.color.chat_bubble_bg_assist)
                        },
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (!isUser && message.reasoningContent.isNotBlank()) {
                        ReasoningSection(
                            message = message,
                            expanded = reasoningExpanded,
                            showReasoning = shouldShowReasoning,
                            onToggleExpanded = { reasoningExpanded = !reasoningExpanded }
                        )

                        if (message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (message.content.isNotBlank()) {
                        if (isUser) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Black
                            )
                        } else {
                            MarkdownText(
                                text = message.content,
                                modifier = Modifier.fillMaxWidth(),
                                textColor = Color.Black
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ReasoningSection(
    message: ChatMessage,
    expanded: Boolean,
    showReasoning: Boolean,
    onToggleExpanded: () -> Unit
) {
    val summaryColor = Color.Black.copy(alpha = 0.62f)
    val reasoningColor = Color.Black.copy(alpha = 0.54f)

    if (message.isThinking) {
        Text(
            text = "思考中...",
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
    } else {
        Text(
            text = buildReasoningSummary(message.reasoningDurationMs, expanded),
            modifier = Modifier.clickable(onClick = onToggleExpanded),
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor
        )
    }

    if (showReasoning) {
        Text(
            text = message.reasoningContent,
            style = MaterialTheme.typography.bodyMedium,
            color = reasoningColor
        )
    }
}

private fun buildReasoningSummary(reasoningDurationMs: Long?, expanded: Boolean): String {
    val suffix = if (expanded) "v" else ">"
    val seconds = ((reasoningDurationMs ?: 0L) + 999L) / 1000L
    val displaySeconds = if (seconds <= 0L) 1L else seconds
    return "已思考（用时 ${displaySeconds} 秒）$suffix"
}

/**
 * 聊天消息列表
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    var previousLastMessageId by remember { mutableStateOf<String?>(null) }

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val totalItems = listState.layoutInfo.totalItemsCount

            // 1. 最后一条 item 必须是最新消息
            if (lastVisibleItem.index < totalItems - 1) return@derivedStateOf false

            // 2. 最后一条 item 的底部必须在视口内（考虑 contentPadding）
            val itemBottom = lastVisibleItem.offset + lastVisibleItem.size
            val viewportBottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
            itemBottom <= viewportBottom
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .distinctUntilChanged()
            .collect { (isScrolling, atBottom) ->
                if (isAutoScrolling) return@collect
                if (isScrolling || atBottom) {
                    shouldFollowBottom = atBottom
                }
            }
    }

    suspend fun scrollLastMessageToBottom(jumpToLastMessage: Boolean) {
        if (messages.isEmpty()) return

        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it == messages.size }

        repeat(2) { pass ->
            withFrameNanos { }

            var lastVisibleItem = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == messages.lastIndex }

            if (lastVisibleItem == null) {
                if (!jumpToLastMessage && pass > 0) return

                listState.scrollToItem(messages.lastIndex)
                withFrameNanos { }
                lastVisibleItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == messages.lastIndex }
                    ?: return
            }

            val viewportBottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
            val overflow = lastVisibleItem.offset + lastVisibleItem.size - viewportBottom
            if (overflow > 0) {
                listState.scrollBy(overflow.toFloat())
            } else {
                return
            }
        }
    }

    val lastMessage = messages.lastOrNull()
    LaunchedEffect(lastMessage?.id, lastMessage?.content, lastMessage?.reasoningContent, lastMessage?.isThinking, shouldFollowBottom) {
        if (lastMessage == null) {
            previousLastMessageId = null
            return@LaunchedEffect
        }

        val jumpToLastMessage = lastMessage.id != previousLastMessageId
        previousLastMessageId = lastMessage.id

        if (!shouldFollowBottom) return@LaunchedEffect

        isAutoScrolling = true
        try {
            scrollLastMessageToBottom(jumpToLastMessage = jumpToLastMessage)
        } finally {
            isAutoScrolling = false
        }
    }

    if (messages.isEmpty()) {
        // 空状态欢迎页
        EmptyChatPlaceholder(modifier = modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                ChatMessageItem(message = message)
            }
        }
    }
}

/**
 * 空聊天状态占位
 */
@Composable
fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
//            Icon(
//                imageVector = Icons.Outlined.ChatBubbleOutline,
//                contentDescription = null,
//                modifier = Modifier.size(64.dp),
//                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
//            )
//            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "XN Agent",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择模式与模型，开始对话",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
