package tech.xiaoniu.xnagent.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import tech.xiaoniu.xnagent.R
import tech.xiaoniu.xnagent.ui.component.ChatMessageList
import tech.xiaoniu.xnagent.ui.component.DropdownSelector
import tech.xiaoniu.xnagent.ui.component.UserAvatar
import tech.xiaoniu.xnagent.ui.model.AgentMode
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.HomeUiState
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.ModelUiModel
import tech.xiaoniu.xnagent.ui.model.SessionUiModel
import tech.xiaoniu.xnagent.ui.model.partitionByPin


/**
 * 主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    initialSessionId: String? = null,
    onConsumeInitialSessionId: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState(HomeUiState())
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeIntent.Initialize)
    }

    // 上层（例如设置页添加智能体）若指定了会话 ID，在首次进入时选中并消费，
    // 避免后续重复触发或被路由切换再次读到。
    LaunchedEffect(initialSessionId) {
        val sessionId = initialSessionId
        if (!sessionId.isNullOrBlank()) {
            viewModel.dispatch(HomeIntent.SelectSession(sessionId))
            onConsumeInitialSessionId()
        }
    }

    HomeScreenContent(
        modifier = modifier,
        uiState = uiState,
        onAction = { viewModel.dispatch(it) },
        keyboardController = keyboardController,
        onOpenSettings = onOpenSettings,
        onOpenLogin = onOpenLogin,
    )
}

/**
 * 将 UI 提取到一个不依赖 Hilt/ViewModel 的可重用 composable 中，以便在 Preview 中使用。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    uiState: HomeUiState = HomeUiState(),
    onAction: (HomeIntent) -> Unit = {},
    initialDrawerValue: DrawerValue = DrawerValue.Closed,
    keyboardController: SoftwareKeyboardController? = null,
    onOpenSettings: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialDrawerValue)
    val drawerScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 抽屉中正在展示菜单的会话 ID；为 null 表示菜单关闭。
    var menuSessionId by remember { mutableStateOf<String?>(null) }
    // 正在重命名的会话；非 null 时弹出重命名对话框。
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 一次性错误事件：errorMessage 变化时弹 Snackbar，然后立即消费避免重复展示。
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            onAction(HomeIntent.ConsumeError)
        }
    }

    val currentSession = uiState.sessions.firstOrNull { it.id == uiState.currentSessionId }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContent(
                modifier = modifier,
                sessions = uiState.sessions,
                menuSessionId = menuSessionId,
                onDismissMenu = { menuSessionId = null },
                onNewChat = {
                    onAction(HomeIntent.CreateNewChat)
                    drawerScope.launch { drawerState.close() }
                },
                onSessionClick = {
                    onAction(HomeIntent.SelectSession(it))
                    drawerScope.launch { drawerState.close() }
                },
                onSessionLongPress = { menuSessionId = it },
                onRenameSession = { session ->
                    menuSessionId = null
                    renameTarget = session.id to session.title
                },
                onTogglePinSession = { session ->
                    onAction(HomeIntent.SetSessionPinned(session.id, !session.isPinned))
                    menuSessionId = null
                },
                onDeleteSession = { session ->
                    onAction(HomeIntent.DeleteSession(session.id))
                    menuSessionId = null
                },
                isGuest = uiState.isGuest,
                viewerName = uiState.viewerName,
                viewerEmail = uiState.viewerEmail,
                onOpenSettings = {
                    drawerScope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onOpenLogin = {
                    drawerScope.launch { drawerState.close() }
                    onOpenLogin()
                },
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) { data -> Snackbar(snackbarData = data) } },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.padding(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                {
                                    drawerScope.launch {
                                        if (drawerState.isClosed) {
                                            drawerState.open()
                                        } else {
                                            drawerState.close()
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Transparent
                                ),
                                modifier = Modifier.padding(0.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_action_menu),
                                    contentDescription = "Menu",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.Transparent)
                                        .padding(6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // 顶部标题按会话类型切换：新对话 / 智能体名称 / 普通会话标题；
                            // 单行省略避免顶栏被长标题撑高。
                            Text(
                                text = topBarTitle(uiState, currentSession),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                                    .copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f),
                            )
                            // 标题生成中提示：用户已经发出首条消息、正在等 LLM 返回标题。
                            if (uiState.isGeneratingTitle) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        // 模型选择器从标题区搬到 actions，与标题文本解耦避免视觉拥挤。
                        // 用 widthIn + heightIn 让宽度在小屏上自适应，避免与标题文本争抢空间导致截断。
                        DropdownSelector(
                            items = uiState.availableModels,
                            onItemSelect = { onAction(HomeIntent.SelectModel(it)) },
                            itemToText = { label },
                            border = false,
                            contentPadding = PaddingValues(),
                            modifier = Modifier
                                .widthIn(min = 88.dp, max = 140.dp)
                                .heightIn(min = 36.dp, max = 36.dp)
                        ) {
                            Text(
                                text = uiState.currentModel?.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(
                            onClick = { onAction(HomeIntent.CreateNewChat) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = "Add",
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Transparent)
                                    .padding(6.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    ),
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // 主内容区展示当前会话消息，并透传编辑、重试、删除与收藏动作。
                ChatMessageList(
                    messages = uiState.messages,
                    onEditUserMessage = { messageId, content ->
                        onAction(HomeIntent.EditUserMessage(messageId, content))
                    },
                    onRegenerateAssistantMessage = {
                        onAction(HomeIntent.RegenerateAssistantMessage(it))
                    },
                    onDeleteMessage = {
                        onAction(HomeIntent.DeleteMessage(it))
                    },
                    onFavoriteMessage = {
                        onAction(HomeIntent.FavoriteMessage(it))
                    },
                    favoritedMessageIds = uiState.favoriteMessageIds,
                    isResponding = uiState.isResponding,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // 游客达到单会话消息上限时，在输入区上方直接提示登录入口。
                if (uiState.isGuestMessageLimitReached) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 文案与 HomeViewModel.GUEST_USER_MESSAGE_LIMIT 保持一致，未来抽 strings.xml 时一起迁移。
                        Text(
                            text = "游客模式每个会话最多发送 ${HomeViewModel.GUEST_USER_MESSAGE_LIMIT} 条消息，当前已发送 ${uiState.guestUserMessageCount} 条。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenLogin) {
                            Text("登录")
                        }
                        TextButton(onClick = onOpenLogin) {
                            Text("注册")
                        }
                    }
                }

                // 底部输入区统一承载输入框、深度思考开关和发送按钮。
                Card(
                    elevation = CardDefaults.cardElevation(4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp, 6.dp)
                        .background(Color.White)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = uiState.inputText,
                                onValueChange = { onAction(HomeIntent.UpdateInput(it)) },
                                placeholder = {
                                    Text(
                                        text = "输入消息…",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent
                                ),
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(0.dp)
                            )
                        }
                        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                            OutlinedButton(
                                modifier = Modifier.defaultMinSize(minHeight = 1.dp),
                                onClick = { onAction(HomeIntent.ToggleDeepThinking) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (uiState.isDeepThinkingEnabled) {
                                        colorResource(R.color.deepthink_bg_color_checked)
                                    } else {
                                        colorResource(R.color.deepthink_bg_color_unchecked)
                                    },
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = "",
                                    tint = if (uiState.isDeepThinkingEnabled) {
                                        colorResource(R.color.deepthink_text_color_checked)
                                    } else {
                                        colorResource(R.color.deepthink_text_color_unchecked)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(2.dp))
                                Text(
                                    text = "深度思考",
                                    fontSize = 12.sp,
                                    color = if (uiState.isDeepThinkingEnabled) {
                                        colorResource(R.color.deepthink_text_color_checked)
                                    } else {
                                        colorResource(R.color.deepthink_text_color_unchecked)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = {
                                    if (uiState.isResponding) {
                                        onAction(HomeIntent.CancelMessage)
                                    } else if (uiState.inputText.isNotBlank() && !uiState.isGuestMessageLimitReached) {
                                        onAction(HomeIntent.SendMessage)
                                        keyboardController?.hide()
                                    }
                                },
                                enabled = uiState.isResponding ||
                                    (uiState.inputText.isNotBlank() && !uiState.isGuestMessageLimitReached)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isResponding) {
                                        Icons.Filled.Stop
                                    } else {
                                        Icons.AutoMirrored.Filled.Send
                                    },
                                    contentDescription = if (uiState.isResponding) "停止" else "发送",
                                    modifier = Modifier.padding(2.dp),
                                    tint = colorResource(R.color.chat_send_button_bg)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按菜单改为内嵌到 SessionRow 内部（见 [SessionRow]），这样 DropdownMenu 会锚定到该行，
    // 不再需要在 HomeScreenContent 顶层另起一个无 anchor 的菜单。

    // 重命名对话框：标题预填当前 title，trim 后非空才允许保存。
    renameTarget?.let { (targetId, initialTitle) ->
        var draft by remember(targetId, initialTitle) { mutableStateOf(initialTitle) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(HomeIntent.RenameSession(targetId, draft.trim()))
                        renameTarget = null
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 顶部栏标题派生。
 *
 * - 未选中会话或正在新建：返回「新对话」；
 * - 智能体会话：返回智能体名称；
 * - 普通会话：返回标题。
 */
private fun topBarTitle(uiState: HomeUiState, currentSession: SessionUiModel?): String {
    if (uiState.currentSessionId == null) return "新对话"
    return currentSession?.displayTitle ?: "新对话"
}

/**
 * 左侧抽屉内容。
 *
 * 上半区按置顶分组展示历史会话，下半区展示当前用户入口与设置入口。
 */
@Composable
fun DrawerContent(
    modifier: Modifier = Modifier,
    sessions: List<SessionUiModel>,
    menuSessionId: String? = null,
    onDismissMenu: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onSessionClick: (String) -> Unit = {},
    onSessionLongPress: (String) -> Unit = {},
    onRenameSession: (SessionUiModel) -> Unit = {},
    onTogglePinSession: (SessionUiModel) -> Unit = {},
    onDeleteSession: (SessionUiModel) -> Unit = {},
    isGuest: Boolean = false,
    viewerName: String = "",
    viewerEmail: String = "",
    onOpenSettings: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f)
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "聊天记录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNewChat) {
                Text("新对话")
            }
        }

        if (sessions.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text("暂无会话", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val (pinnedSessions, normalSessions) = sessions.partitionByPin()
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // 置顶组：仅在存在置顶会话时显示小标题；置顶会话按更新时间倒序（partitionByPin 已处理）。
                if (pinnedSessions.isNotEmpty()) {
                    item(key = "header_pinned") {
                        Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        count = pinnedSessions.size,
                        key = { "pinned_${pinnedSessions[it].id}" },
                    ) { i ->
                        val session = pinnedSessions[i]
                        SessionRow(
                            session = session,
                            isMenuExpanded = menuSessionId == session.id,
                            onSessionClick = onSessionClick,
                            onSessionLongPress = onSessionLongPress,
                            onDismissMenu = onDismissMenu,
                            onRename = { onRenameSession(session) },
                            onTogglePin = { onTogglePinSession(session) },
                            onDelete = { onDeleteSession(session) },
                        )
                    }
                }

                // 普通组：无小标题，直接列出；时间倒序（partitionByPin 已处理）。
                items(
                    count = normalSessions.size,
                    key = { "normal_${normalSessions[it].id}" },
                ) { i ->
                    val session = normalSessions[i]
                    SessionRow(
                        session = session,
                        isMenuExpanded = menuSessionId == session.id,
                        onSessionClick = onSessionClick,
                        onSessionLongPress = onSessionLongPress,
                        onDismissMenu = onDismissMenu,
                        onRename = { onRenameSession(session) },
                        onTogglePin = { onTogglePinSession(session) },
                        onDelete = { onDeleteSession(session) },
                    )
                }
            }
        }

        // 抽屉底部固定展示当前身份信息，并提供设置或登录入口。
        if (isGuest) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLogin)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    label = "游客",
                    size = 42.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "游客模式",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "前往登录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clickable(onClick = onOpenSettings)) {
                    UserAvatar(
                        label = viewerName.ifBlank { viewerEmail.ifBlank { "XN" } },
                        size = 42.dp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewerName.ifBlank { viewerEmail.substringBefore('@').ifBlank { "XN 用户" } },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (viewerEmail.isNotBlank()) {
                        Text(
                            text = viewerEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "二维码扫描",
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设置",
                    )
                }
            }
        }
    }
}

/**
 * 抽屉中的单个会话条目。
 *
 * - 智能体会话：左侧展示「智能体首字母」占位头像 + 智能体名称；
 * - 普通会话：不显示头像，直接显示标题；
 * - 长按触发 [onSessionLongPress]，短按触发 [onSessionClick]；
 * - 单行省略避免侧栏被长标题撑爆。
 * - 长按菜单作为子组件渲染在本行 Box 内，DropdownMenu 的 anchor 自动绑定到该行，
 *   避免脱离 anchor 跑到屏幕中央或原点的问题。
 */
@Composable
private fun SessionRow(
    session: SessionUiModel,
    isMenuExpanded: Boolean,
    onSessionClick: (String) -> Unit,
    onSessionLongPress: (String) -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (session.selected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .combinedClickable(
                    onClick = { onSessionClick(session.id) },
                    onLongClick = { onSessionLongPress(session.id) },
                )
                .padding(horizontal = if (session.isAgent) 12.dp else 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (session.isAgent) {
                UserAvatar(
                    label = session.agentName ?: session.displayTitle,
                    size = 28.dp,
                )
            }
            Text(
                text = session.displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onDismissMenu,
        ) {
            if (!session.isAgent) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = onRename,
                )
            }
            DropdownMenuItem(
                text = { Text(if (session.isPinned) "取消置顶" else "置顶") },
                onClick = onTogglePin,
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = onDelete,
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreenContent(
        uiState = HomeUiState(
            agentMode = AgentMode.entries.first(),
            inputText = "示例消息",
            isResponding = true,
            currentModel = ModelUiModel(
                "gpt-3.5-turbo",
                "GPT-3.5 Turbo",
                "OpenAI",
            ),
            availableModels = listOf(
                ModelUiModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI"),
                ModelUiModel("gpt-4", "GPT-4", "OpenAI"),
                ModelUiModel("custom-agent", "自定义 Agent", "本地部署")
            ),
            messages = listOf(
                ChatMessage(
                    id = "1",
                    content = "你好！有什么我可以帮助你的吗？",
                    role = MessageRole.ASSISTANT,
                ),
                ChatMessage(
                    id = "2",
                    content = "请介绍一下你自己。",
                    role = MessageRole.USER,
                ),
                ChatMessage(
                    id = "3",
                    content = "我是一个由 OpenAI 训练的语言模型，旨在帮助用户  解答问题、提供信息和进行对话交流。",
                    role = MessageRole.ASSISTANT,
                ),
                ChatMessage(
                    id = "4",
                    content = "",
                    role = MessageRole.ASSISTANT,
                    isGenerating = true,
                ),
                ChatMessage(
                    id = "5",
                    content = "",
                    role = MessageRole.ASSISTANT,
                    reasoningContent = "",
                    isThinking = true,
                    isGenerating = true,
                )
            ),
            sessions = System.currentTimeMillis().let { now ->
                listOf(
                    SessionUiModel("1", "会话 1", isPinned = true, updatedAt = now),
                    SessionUiModel("2", "会话 2", isPinned = false, updatedAt = now - 3600_000),
                    SessionUiModel(
                        "3",
                        "会话 3",
                        isPinned = false,
                        agentId = "agent-x",
                        agentName = "小奶茉",
                        updatedAt = now - 7200_000,
                    ),
                    SessionUiModel("4", "会话 4", isPinned = false, updatedAt = now - 72000_000),
                    SessionUiModel("5", "会话 5", isPinned = false, updatedAt = now - 122000_000),
                )
            }
        ),
        initialDrawerValue = DrawerValue.Closed
    )
}
