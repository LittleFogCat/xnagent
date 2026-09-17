package tech.xiaoniu.xnagent.ui.screen.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 智能体详情页。
 *
 * @param agentId 目标智能体 ID；为 null 表示新建自定义智能体。
 * @param onBack 返回上一页（设置页）。
 * @param onOpenChat 打开该智能体的会话。
 */
@Composable
fun AgentDetailScreen(
    agentId: String?,
    onBack: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    viewModel: AgentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Activity 级 hiltViewModel 会跨 agentId 复用同一实例，必须显式按 agentId 重置表单。
    LaunchedEffect(agentId) {
        viewModel.dispatch(AgentDetailIntent.Load(agentId))
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AgentDetailEvent.OpenChat -> onOpenChat(event.sessionId)
                AgentDetailEvent.Closed -> onBack()
            }
        }
    }

    AgentDetailContent(
        uiState = uiState,
        onAction = { viewModel.dispatch(it) },
        onBack = onBack,
    )
}

/**
 * 抽出不依赖 Hilt 的展示层，便于 Preview。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailContent(
    uiState: AgentDetailUiState = AgentDetailUiState(),
    onAction: (AgentDetailIntent) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.noticeMessage) {
        val message = uiState.noticeMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            onAction(AgentDetailIntent.ConsumeNotice)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.agentId == null) "新建智能体" else "智能体详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) { Snackbar(snackbarData = it) } },
        containerColor = Color.White,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // 有服务端 role 的智能体（系统智能体）只做只读展示，需求未要求可编辑。
            if (uiState.role.isNotBlank()) {
                FieldLabel("定位")
                Text(
                    text = uiState.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            if (uiState.isCustom) {
                Badge(text = "自定义智能体")
                Spacer(modifier = Modifier.height(12.dp))
            } else if (uiState.isOverridden) {
                Badge(text = "已按本地修改覆盖服务端配置")
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (uiState.isHidden) {
                Badge(text = "已隐藏，不在列表中展示")
                Spacer(modifier = Modifier.height(12.dp))
            }

            FieldLabel("名称")
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { onAction(AgentDetailIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("给智能体取个名字") },
                shape = MaterialTheme.shapes.extraLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FieldLabel("简介")
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { onAction(AgentDetailIntent.UpdateDescription(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                placeholder = { Text("一句话说明它能做什么") },
                shape = MaterialTheme.shapes.extraLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FieldLabel("提示词")
            OutlinedTextField(
                value = uiState.prompt,
                onValueChange = { onAction(AgentDetailIntent.UpdatePrompt(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                placeholder = { Text("在这里写下它的身份与行为准则，发送消息时会作为系统提示词带入") },
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (uiState.prompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "写了提示词后，该智能体的会话将以本地提示词为准，不再使用服务端人格。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onAction(AgentDetailIntent.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSave,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("保存")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 「添加对话」是独立动作：已有绑定会话时直接打开，否则新建一条。
            OutlinedButton(
                onClick = { onAction(AgentDetailIntent.OpenChat) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isBusy && uiState.name.isNotBlank(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(if (uiState.boundSessionId.isNullOrBlank()) "添加对话" else "打开已有对话")
            }

            if (uiState.agentId != null) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onAction(AgentDetailIntent.SetHidden(!uiState.isHidden)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBusy,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(
                        imageVector = if (uiState.isHidden) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(if (uiState.isHidden) "取消隐藏" else "隐藏智能体")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBusy,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("删除智能体")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 删除不可恢复，单独二次确认。
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除智能体") },
                text = { Text("删除后该智能体不再出现在列表中，且无法恢复。已创建的历史会话仍会保留。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onAction(AgentDetailIntent.Delete)
                        },
                    ) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun Badge(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AgentDetailContentPreview() {
    AgentDetailContent(
        uiState = AgentDetailUiState(
            agentId = "123",
            name = "Rosetta",
            role = "assistant",
            description = "帮助梳理需求的助手",
            prompt = "你是一位严谨的产品助手。",
            isOverridden = true,
        ),
    )
}
