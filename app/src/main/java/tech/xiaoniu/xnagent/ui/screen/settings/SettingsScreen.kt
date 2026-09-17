package tech.xiaoniu.xnagent.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import tech.xiaoniu.xnagent.BuildConfig
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.ui.component.IosMenuDivider
import tech.xiaoniu.xnagent.ui.component.UserAvatar
import tech.xiaoniu.xnagent.ui.model.AgentUiModel
import tech.xiaoniu.xnagent.ui.model.MessageHighlight

private enum class SettingsSection {
    AGENTS,
    HIDDEN_AGENTS,
    FAVORITES,
}

/** 列表展开 / 收起动画时长，与思考过程展开保持一致。 */
private const val SECTION_ANIM_DURATION_MS = 220

/** 设置页，按用户信息、智能体、收藏和数据管理几个区域组织。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isGuest: Boolean,
    displayName: String,
    email: String,
    onBack: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    onOpenFavorite: (String, MessageHighlight) -> Unit = { _, _ -> },
    onOpenAgentDetail: (String?) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showClearLocalDataDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    val noticeMessage = uiState.noticeMessage
    val snackbarHostState = remember { SnackbarHostState() }

    // 智能体 / 收藏的跳转由 ViewModel 发事件，上层负责切页。
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.OpenFavorite -> onOpenFavorite(event.sessionId, event.highlight)
            }
        }
    }

    // 提示走 Snackbar 而不是页面底部的一行文字：设置页是可长滚动页面，放在底部用户看不到，
    // 观感上等同于「点了没反应」。
    LaunchedEffect(noticeMessage) {
        if (!noticeMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = noticeMessage,
                duration = SnackbarDuration.Short,
            )
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) { Snackbar(snackbarData = it) } },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
        containerColor = Color.White,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // 顶部用户信息区展示头像、昵称和邮箱。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    label = displayName.ifBlank { email.ifBlank { "游客" } },
                    size = 92.dp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 智能体区：展示可用智能体（服务端 + 本地自定义），点击进详情页管理。
            val visibleAgents = uiState.agents.filterNot { it.isHidden }
            val hiddenAgents = uiState.agents.filter { it.isHidden }
            SettingsRow(
                icon = Icons.Outlined.Hub,
                title = "智能体",
                desc = if (visibleAgents.isEmpty()) "暂无可用智能体" else "${visibleAgents.size} 个可用智能体",
                expandable = true,
                expanded = expandedSection == SettingsSection.AGENTS,
                onClick = {
                    expandedSection = if (expandedSection == SettingsSection.AGENTS) null else SettingsSection.AGENTS
                },
            )
            AnimatedVisibility(
                visible = expandedSection == SettingsSection.AGENTS,
                enter = expandVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
                exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
            ) {
                SettingsSectionCard {
                    // 新建入口固定在最上方，避免被长列表挤到看不见的地方。
                    AgentCreateItem(onClick = { onOpenAgentDetail(null) })
                    if (visibleAgents.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFFEDEDED))
                    }
                    visibleAgents.forEachIndexed { index, agent ->
                        AgentItem(
                            agent = agent,
                            onClick = { onOpenAgentDetail(agent.id) },
                        )
                        if (index != visibleAgents.lastIndex) {
                            HorizontalDivider(color = Color(0xFFEDEDED))
                        }
                    }
                    if (visibleAgents.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "当前没有可用的智能体",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 隐藏区只在存在隐藏项时出现，提供恢复入口。
            if (hiddenAgents.isNotEmpty()) {
                SettingsRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "已隐藏的智能体",
                    desc = "${hiddenAgents.size} 个已隐藏",
                    expandable = true,
                    expanded = expandedSection == SettingsSection.HIDDEN_AGENTS,
                    onClick = {
                        expandedSection = if (expandedSection == SettingsSection.HIDDEN_AGENTS) {
                            null
                        } else {
                            SettingsSection.HIDDEN_AGENTS
                        }
                    },
                )
                AnimatedVisibility(
                    visible = expandedSection == SettingsSection.HIDDEN_AGENTS,
                    enter = expandVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
                    exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
                ) {
                    SettingsSectionCard {
                        hiddenAgents.forEachIndexed { index, agent ->
                            HiddenAgentItem(
                                agent = agent,
                                onRestore = { viewModel.setAgentHidden(agent.id, false) },
                            )
                            if (index != hiddenAgents.lastIndex) {
                                HorizontalDivider(color = Color(0xFFEDEDED))
                            }
                        }
                    }
                }
            }

            // 收藏区集中展示已收藏消息，并支持直接移除。
            SettingsRow(
                icon = Icons.Outlined.BookmarkBorder,
                title = "我的收藏",
                desc = if (uiState.favorites.isEmpty()) "尚未收藏任何消息" else "${uiState.favorites.size} 条收藏",
                expandable = true,
                expanded = expandedSection == SettingsSection.FAVORITES,
                onClick = {
                    expandedSection = if (expandedSection == SettingsSection.FAVORITES) null else SettingsSection.FAVORITES
                },
            )
            AnimatedVisibility(
                visible = expandedSection == SettingsSection.FAVORITES,
                enter = expandVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
                exit = shrinkVertically(animationSpec = tween(SECTION_ANIM_DURATION_MS)),
            ) {
                SettingsSectionCard {
                    if (uiState.favorites.isEmpty()) {
                        Text(
                            text = "暂时还没有收藏的消息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.favorites.forEachIndexed { index, favorite ->
                            FavoriteItem(
                                favorite = favorite,
                                onOpen = { viewModel.openFavorite(favorite) },
                                onRemove = { viewModel.removeFavorite(favorite.id) },
                            )
                            if (index != uiState.favorites.lastIndex) {
                                HorizontalDivider(color = Color(0xFFEDEDED))
                            }
                        }
                    }
                }
            }

            // 数据管理区提供本地清理和账号入口，都是设置页的操作性区域。
            SettingsRow(
                icon = Icons.Outlined.DeleteOutline,
                title = "清除本地数据",
                desc = if (uiState.isClearingLocalData) {
                    "正在清理本地数据..."
                } else {
                    "清除本地聊天记录、智能体、收藏与登录状态"
                },
                enabled = !uiState.isClearingLocalData,
                onClick = { showClearLocalDataDialog = true },
            )

            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = if (isGuest) "前往登录" else "退出登录",
                desc = if (isGuest) "登录后解锁完整能力" else null,
                onClick = {
                    if (isGuest) {
                        onBackToLogin()
                    } else {
                        showLogoutDialog = true
                    }
                },
            )

            // 底部区域展示当前版本号（一次性提示已改走 Snackbar）。
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // 清理本地数据属于不可逆操作，需要额外确认。
        if (showClearLocalDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearLocalDataDialog = false },
                title = { Text("清除本地数据") },
                text = {
                    Text("将删除本地聊天记录、智能体、收藏内容和登录状态，此操作不可恢复。远端聊天记录不会被删除。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearLocalDataDialog = false
                            viewModel.clearLocalData()
                        },
                        enabled = !uiState.isClearingLocalData,
                    ) {
                        Text("确认清除")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearLocalDataDialog = false },
                        enabled = !uiState.isClearingLocalData,
                    ) {
                        Text("取消")
                    }
                },
            )
        }

        // 退出登录会清空登录态，单独确认以避免误触。
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("退出登录") },
                text = {
                    Text("确定要退出当前账号吗？退出后仍会保留你的邮箱，方便下次直接登录。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onBackToLogin()
                        },
                    ) {
                        Text("确认退出")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutDialog = false },
                    ) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String? = null,
    enabled: Boolean = true,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
) {
    // 可展开行右侧箭头按状态旋转：收起向右，展开后向下。
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = SECTION_ANIM_DURATION_MS),
        label = "settingsRowArrow",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expandable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
        }
        HorizontalDivider(color = Color(0xFFEDEDED))
    }
}

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 「新建智能体」入口行。 */
@Composable
private fun AgentCreateItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "新建智能体",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AgentItem(
    agent: AgentUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            label = agent.name,
            size = 44.dp,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = agent.name, style = MaterialTheme.typography.titleMedium)
            if (agent.role.isNotBlank()) {
                Text(
                    text = agent.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (agent.description.isNotBlank()) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 「已添加」表示该智能体已有一条绑定会话，点进去可以直接打开而不是重新创建。
        if (agent.isBound) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "已添加",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** 隐藏区条目，只提供恢复操作。 */
@Composable
private fun HiddenAgentItem(
    agent: AgentUiModel,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(label = agent.name, size = 40.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = agent.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) {
            Text("恢复")
        }
    }
}

/**
 * 收藏条目。
 *
 * 交互参考微信收藏：点击打开对应会话并定位到该条消息，长按弹出菜单（复制 / 删除）。
 * 不再常驻删除按钮，避免误触与视觉噪音。
 */
@Composable
private fun FavoriteItem(
    favorite: FavoriteMessage,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var showActionMenu by remember(favorite.id) { mutableStateOf(false) }
    var actionMenuOffset by remember(favorite.id) { mutableStateOf(DpOffset.Zero) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 轻点与长按交给同一个 detector：clickable + 另一个 pointerInput 会让两个检测器
                // 争抢同一串指针事件（谁消费掉 up 谁说了算），合成一个才可控。
                // 顺带拿到按压点坐标，DropdownMenu 才能锚到手指位置（见 design-system.md §6.7）。
                .pointerInput(favorite.id) {
                    detectTapGestures(
                        onTap = { onOpen() },
                        onLongPress = { pressOffset ->
                            actionMenuOffset = with(density) {
                                DpOffset(pressOffset.x.toDp(), pressOffset.y.toDp())
                            }
                            showActionMenu = true
                        },
                    )
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = favorite.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 0 大小锚点 Box：DropdownMenu 必须嵌在它内部，Popup 才会以按压点为 anchor。
        if (showActionMenu) {
            Box(
                modifier = Modifier
                    .offset(actionMenuOffset.x, actionMenuOffset.y)
                    .size(0.dp)
            ) {
                DropdownMenu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    offset = DpOffset.Zero,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    containerColor = Color.White.copy(alpha = 0.96f),
                    border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.06f)),
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            clipboardManager?.setPrimaryClip(
                                ClipData.newPlainText("favorite", favorite.content)
                            )
                            showActionMenu = false
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                    IosMenuDivider()
                    DropdownMenuItem(
                        text = { Text("删除收藏") },
                        onClick = {
                            showActionMenu = false
                            onRemove()
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}