package tech.xiaoniu.xnagent.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import tech.xiaoniu.xnagent.ui.model.groupByDate


/**
 * 主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState(HomeUiState())
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeIntent.Initialize)
    }

    HomeScreenContent(
        modifier = modifier,
        uiState = uiState,
        onAction = { viewModel.dispatch(it) },
        keyboardController = keyboardController,
        onOpenSettings = onOpenSettings,
        onOpenLogin = onOpenLogin,
//        agentMode = agentMode,
//        inputText = inputText,
//        messages = messages,
//        currentModel = currentModel,
//        models = viewModel.availableModels,
//        onModelSelected = { viewModel.selectModel(it) },
//        onAgentModeChange = { viewModel.setAgentMode(it) },
//        onInputChange = { viewModel.updateInput(it) },
//        onSend = {
//            viewModel.sendMessage()
//            keyboardController?.hide()
//        }
    )
}

/**
 * 将 UI 提取到一个不依赖 Hilt/ViewModel 的可重用 composable 中，以便在 Preview 中使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 抽屉区域负责会话切换、创建新对话以及进入设置/登录。
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            DrawerContent(
                modifier = modifier,
                sessions = uiState.sessions,
                onNewChat = {
                    onAction(HomeIntent.CreateNewChat)
                    drawerScope.launch { drawerState.close() }
                },
                onSessionClick = {
                    onAction(HomeIntent.SelectSession(it))
                    drawerScope.launch { drawerState.close() }
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
            topBar = {
                // 顶部栏提供抽屉开关、当前模型选择和新建会话入口。
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
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "XN",
                                    style = MaterialTheme.typography.bodyLarge
                                        .copy(fontWeight = FontWeight.Bold)
                                )
                                DropdownSelector(
                                    items = uiState.availableModels,
                                    onItemSelect = { onAction(HomeIntent.SelectModel(it)) },
                                    itemToText = { label },
                                    border = false,
                                    contentPadding = PaddingValues(),
                                    modifier = Modifier
                                        .size(140.dp, 30.dp)
                                ) {
                                    Text(
                                        text = uiState.currentModel?.name ?: "",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    },
                    actions = {
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
//                        DropdownSelector(
//                            items = AgentMode.entries.toList(),
//                            onItemSelect = { onAction(HomeIntent.SetAgentMode(it)) },
//                            itemToText = { name },
//                            modifier = Modifier.size(120.dp, 40.dp)
//                        ) {
//                            Text(
//                                uiState.agentMode.name,
//                                modifier = Modifier.fillMaxWidth(),
//                            )
//                        }
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
                        Text(
                            text = "游客模式每个会话最多发送 10 条消息，当前已发送 ${uiState.guestUserMessageCount} 条。",
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
}

/**
 * 左侧抽屉内容。
 *
 * 上半区展示历史会话，下半区展示当前用户入口与设置入口。
 */
@Composable
fun DrawerContent(
    modifier: Modifier = Modifier,
    sessions: List<SessionUiModel>,
    onNewChat: () -> Unit = {},
    onSessionClick: (String) -> Unit = {},
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

        // 会话列表按日期分组展示，便于在历史记录较多时快速定位。
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
            val sessionGroups = sessions.groupByDate()
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                sessionGroups.forEach { group ->
                    val groupId = "header_${group.groupTitle}"
                    item(key = groupId) {
                        Text(
                            text = group.groupTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(
                        count = group.sessions.size,
                        key = {
                            "session_${group.sessions[it].id}"
                        },
                    ) { i ->
                        val session = group.sessions[i]
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (session.selected) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSessionClick(session.id) }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
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
                    SessionUiModel("1", "会话 1", now),
                    SessionUiModel("2", "会话 2", now - 3600_000),
                    SessionUiModel("3", "会话 3", now - 7200_000),
                    SessionUiModel("4", "会话 4", now - 72000_000),
                    SessionUiModel("5", "会话 5", now - 122000_000),
                )
            }
        ),
        initialDrawerValue = DrawerValue.Closed
    )
}

