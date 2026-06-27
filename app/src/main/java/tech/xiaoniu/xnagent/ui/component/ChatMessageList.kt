package tech.xiaoniu.xnagent.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import tech.xiaoniu.xnagent.R
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.MessageRole

private const val REASONING_EXPAND_ANIMATION_DURATION_MS = 220

/**
 * 单条聊天消息项。
 *
 * 除了正文展示外，还负责长按菜单、复制、收藏、重新生成、选择文字和用户消息编辑。
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onEditUserMessage: (String, String) -> Unit = { _, _ -> },
    onRegenerateAssistantMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onFavoriteMessage: (String) -> Unit = {},
    isFavorited: Boolean = false,
) {
    val isUser = message.role == MessageRole.USER

    // 生成中的 assistant 消息如果还没有任何内容（包括 reasoning），先不渲染气泡，由列表底部的 TypingIndicator 顶替。
    if (!isUser && message.content.isBlank() && message.reasoningContent.isBlank() && (message.isThinking || message.isGenerating)) {
        return
    }

    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var reasoningExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var showActionMenu by rememberSaveable(message.id) { mutableStateOf(false) }
    var showSelectionDialog by rememberSaveable("${message.id}_select") { mutableStateOf(false) }
    var showEditDialog by rememberSaveable("${message.id}_edit") { mutableStateOf(false) }
    var editDraft by rememberSaveable(message.id, message.content) { mutableStateOf(message.content) }
    val shouldShowReasoning = message.isThinking || reasoningExpanded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {

        BoxWithConstraints {
            Box(
                modifier = Modifier
                    .widthIn(max = if (isUser) maxWidth * 0.77f else maxWidth)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { showActionMenu = true }
                    )
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
                    // 思考中的消息即使还没有 reasoning 片段，也要展示"思考中..."，避免用户误以为无响应。
                    if (!isUser && (message.reasoningContent.isNotBlank() || message.isThinking)) {
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
                                style = MaterialTheme.typography.bodyMedium,
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

                    if (!isUser && message.content.isNotBlank() && !message.isGenerating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager?.setPrimaryClip(
                                        ClipData.newPlainText("message", message.content)
                                    )
                                },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black.copy(alpha = 0.7f),
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "复制",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onRegenerateAssistantMessage(message.id) },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black.copy(alpha = 0.7f),
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "重新生成",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onFavoriteMessage(message.id) },
                                enabled = !isFavorited,
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black.copy(alpha = 0.7f),
                                    disabledContentColor = Color.Black.copy(alpha = 0.45f),
                                ),
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) {
                                        Icons.Filled.Bookmark
                                    } else {
                                        Icons.Outlined.BookmarkBorder
                                    },
                                    contentDescription = if (isFavorited) "已收藏" else "收藏",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            clipboardManager?.setPrimaryClip(
                                ClipData.newPlainText("message", message.content)
                            )
                            showActionMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                            )
                        },
                    )
                    if (isUser) {
                        DropdownMenuItem(
                            text = { Text("修改并重发") },
                            onClick = {
                                editDraft = message.content
                                showActionMenu = false
                                showEditDialog = true
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("选择文字") },
                        onClick = {
                            showActionMenu = false
                            showSelectionDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isFavorited) "已收藏" else "收藏") },
                        onClick = {
                            if (!isFavorited) {
                                onFavoriteMessage(message.id)
                            }
                            showActionMenu = false
                        },
                        enabled = !isFavorited,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFavorited) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            onDeleteMessage(message.id)
                            showActionMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
        }
    }

    if (showSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text("选择文字") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isUser) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                            )
                        }
                    } else {
                        MarkdownText(
                            text = message.content,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = Color.Black,
                            selectable = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectionDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改消息") },
            text = {
                TextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditUserMessage(message.id, editDraft)
                        showEditDialog = false
                    },
                    enabled = editDraft.trim().isNotBlank(),
                ) {
                    Text("保存并重发")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            },
        )
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
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = REASONING_EXPAND_ANIMATION_DURATION_MS),
        label = "reasoningArrowRotation"
    )

    if (message.isThinking) {
        // reasoning 仍在持续追加时只显示“思考中”，避免用户误以为已经完整结束。
        Text(
            text = "思考中...",
            style = MaterialTheme.typography.bodySmall,
            color = summaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
    } else {
        Row(
            modifier = Modifier.clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        )
        {
            Text(
                text = buildReasoningSummary(message.reasoningDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = if (expanded) "收起思考内容" else "展开思考内容",
                tint = summaryColor,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }
    }

    AnimatedVisibility(
        visible = showReasoning,
        enter = expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(durationMillis = REASONING_EXPAND_ANIMATION_DURATION_MS)
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120)),
    ) {
        Box(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
        ) {
            Text(
                text = message.reasoningContent,
                style = MaterialTheme.typography.bodyMedium,
                color = reasoningColor
            )
        }
    }
}
private fun buildReasoningSummary(reasoningDurationMs: Long?): String {
    val seconds = ((reasoningDurationMs ?: 0L) + 999L) / 1000L
    val displaySeconds = if (seconds <= 0L) 1L else seconds
    return "已思考（用时 ${displaySeconds} 秒）"
}

/**
 * 聊天消息列表。
 *
 * 采用 reverseLayout=true，让最新消息天然锚定在底部，同时单独维护“是否跟随底部”状态，
 * 避免程序化滚动被误判成用户手势导致漏滚。
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onEditUserMessage: (String, String) -> Unit = { _, _ -> },
    onRegenerateAssistantMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onFavoriteMessage: (String) -> Unit = {},
    favoritedMessageIds: Set<String> = emptySet(),
    isResponding: Boolean = false,
) {
    val listState = rememberLazyListState()
    val displayMessages = remember(messages) { messages.asReversed() }
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    val isAtBottom by remember {
        derivedStateOf {
            if (displayMessages.isEmpty()) return@derivedStateOf true

            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 1
        }
    }

    // 用户手动滚离底部后暂停自动跟底；程序化滚动期间不回写这个状态。
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

    // 等待最新 item 真正进入布局后再滚到底部，避免消息刚插入时滚动目标还不存在。
    suspend fun scrollLatestMessageToBottom() {
        if (displayMessages.isEmpty()) return

        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .first { it == displayMessages.size }

        listState.scrollToItem(0)
    }

    val latestMessage = messages.lastOrNull()

    // 新消息到来且仍处于跟底状态时，自动把最新消息贴到底边。
    LaunchedEffect(latestMessage?.id, shouldFollowBottom) {
        if (latestMessage == null || !shouldFollowBottom) {
            return@LaunchedEffect
        }

        isAutoScrolling = true
        try {
            scrollLatestMessageToBottom()
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
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            // TypingIndicator 只在“还没有一个正在增长中的助手气泡”时出现。
            // 一旦首帧 SSE 到达，气泡会代替圆点，避免视觉上同时看到两个“正在生成”提示。
            val hasLiveAssistant = messages.any {
                it.role == MessageRole.ASSISTANT &&
                    (it.isThinking || it.isGenerating)
            }
            if (isResponding && !hasLiveAssistant) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }
            
            items(
                items = displayMessages,
                key = { it.id }
            ) { message ->
                ChatMessageItem(
                    message = message,
                    onEditUserMessage = onEditUserMessage,
                    onRegenerateAssistantMessage = onRegenerateAssistantMessage,
                    onDeleteMessage = onDeleteMessage,
                    onFavoriteMessage = onFavoriteMessage,
                    isFavorited = favoritedMessageIds.contains(message.id),
                )
            }
        }
    }
}

/**
 * 空聊天状态占位。
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

/**
 * 助手正在生成回复时，在聊天框底部、输入框上方显示的"打字机"指示器。
 *
 * 三个圆点循环上下浮动，模拟经典 AI chat 的加载动画。
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    // 使用主题色而非 Color.Black，避免深色模式下亮度脱节；详见 design-system.md §6.6。
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "typingDots")
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 三个点从左到右依次点亮，模拟文字打出方向。
        repeat(3) { idx ->
            val anim by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        1f at (160 + idx * 160)
                        0f at 1200
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "typingDot_$idx",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = with(density) { (-4).dp.toPx() } * anim }
                    .size(8.dp)
                    .background(
                        color = dotColor.copy(alpha = 0.35f + 0.55f * anim),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
        }
    }
}
