package tech.xiaoniu.xnagent.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import tech.xiaoniu.xnagent.BuildConfig
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tech.xiaoniu.xnagent.data.repository.FavoriteMessage
import tech.xiaoniu.xnagent.ui.component.UserAvatar

private enum class SettingsSection {
    AGENTS,
    FAVORITES,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    isGuest: Boolean,
    displayName: String,
    email: String,
    onBack: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showClearLocalDataDialog by rememberSaveable { mutableStateOf(false) }
    val noticeMessage = uiState.noticeMessage

    Scaffold(
        modifier = modifier.fillMaxSize(),
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

            SettingsRow(
                icon = Icons.Outlined.Hub,
                title = "智能体",
                desc = if (uiState.agents.isEmpty()) "暂无可用智能体" else "${uiState.agents.size} 个可用智能体",
                onClick = {
                    expandedSection = if (expandedSection == SettingsSection.AGENTS) null else SettingsSection.AGENTS
                },
            )
            if (expandedSection == SettingsSection.AGENTS) {
                SettingsSectionCard {
                    if (uiState.agents.isEmpty()) {
                        Text(
                            text = "当前没有可添加的公开智能体",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.agents.forEachIndexed { index, agent ->
                            AgentItem(
                                agent = agent,
                                onAdd = { viewModel.addAgentToChat(agent.id) },
                            )
                            if (index != uiState.agents.lastIndex) {
                                HorizontalDivider(color = Color(0xFFEDEDED))
                            }
                        }
                    }
                }
            }

            SettingsRow(
                icon = Icons.Outlined.BookmarkBorder,
                title = "我的收藏",
                desc = if (uiState.favorites.isEmpty()) "尚未收藏任何消息" else "${uiState.favorites.size} 条收藏",
                onClick = {
                    expandedSection = if (expandedSection == SettingsSection.FAVORITES) null else SettingsSection.FAVORITES
                },
            )
            if (expandedSection == SettingsSection.FAVORITES) {
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
                                onRemove = { viewModel.removeFavorite(favorite.id) },
                            )
                            if (index != uiState.favorites.lastIndex) {
                                HorizontalDivider(color = Color(0xFFEDEDED))
                            }
                        }
                    }
                }
            }

            SettingsRow(
                icon = Icons.Outlined.DeleteOutline,
                title = "清除本地数据",
                desc = if (uiState.isClearingLocalData) {
                    "正在清理本地数据..."
                } else {
                    "清除本地聊天记录、收藏与登录状态"
                },
                enabled = !uiState.isClearingLocalData,
                onClick = { showClearLocalDataDialog = true },
            )

            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = if (isGuest) "前往登录" else "退出登录",
                desc = if (isGuest) "登录后解锁完整能力" else null,
                onClick = onBackToLogin,
            )

            if (!noticeMessage.isNullOrBlank()) {
                Text(
                    text = noticeMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        if (showClearLocalDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearLocalDataDialog = false },
                title = { Text("清除本地数据") },
                text = {
                    Text("将删除本地聊天记录、收藏内容和登录状态，此操作不可恢复。远端聊天记录不会被删除。")
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
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
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
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            .background(Color(0xFFF8FAFC), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun AgentItem(
    agent: AgentUiState,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        TextButton(onClick = onAdd, enabled = !agent.added) {
            Text(if (agent.added) "已添加" else "添加")
        }
    }
}

@Composable
private fun FavoriteItem(
    favorite: FavoriteMessage,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "移除收藏",
            )
        }
    }
}