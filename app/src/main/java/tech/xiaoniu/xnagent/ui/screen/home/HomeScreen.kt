package tech.xiaoniu.xnagent.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tech.xiaoniu.xnagent.R
import tech.xiaoniu.xnagent.ui.component.ChatMessageList
import tech.xiaoniu.xnagent.ui.component.DropdownSelector
import tech.xiaoniu.xnagent.ui.model.AgentMode
import tech.xiaoniu.xnagent.ui.model.ChatMessage
import tech.xiaoniu.xnagent.ui.model.HomeUiState
import tech.xiaoniu.xnagent.ui.model.MessageRole
import tech.xiaoniu.xnagent.ui.model.ModelUiModel


/**
 * 主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
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
        keyboardController = keyboardController
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
    keyboardController: SoftwareKeyboardController? = null
//    agentMode: AgentMode,
//    inputText: String,
//    currentModel: ModelUiModel,
//    messages: List<ChatMessage>,
//    models: List<ModelUiModel>,
//    onModelSelected: (ModelUiModel) -> Unit,
//    onAgentModeChange: (AgentMode) -> Unit,
//    onInputChange: (String) -> Unit,
//    onSend: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 顶栏
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            {

                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White
                            ),
                            modifier = Modifier
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_menu),
                                contentDescription = "Menu",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(6.dp)
                            )
                        }
                        Column {
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
                                    text = uiState.currentModel?.label ?: "",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                actions = {
                    DropdownSelector(
                        items = AgentMode.entries.toList(),
                        onItemSelect = { onAction(HomeIntent.SetAgentMode(it)) },
                        itemToText = { name },
                        modifier = Modifier.size(120.dp, 40.dp)
                    ) {
                        Text(
                            uiState.agentMode.name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            // 聊天消息区
            ChatMessageList(
                messages = uiState.messages,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // 底部输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { onAction(HomeIntent.UpdateInput(it)) },
                    placeholder = {
                        Text(
                            text = "输入消息…",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                IconButton(
                    onClick = {
                        if (uiState.inputText.isNotBlank()) {
                            onAction(HomeIntent.SendMessage)
                            keyboardController?.hide()
                        }
                    },
                    enabled = uiState.inputText.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        modifier = Modifier.padding(2.dp)
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
            currentModel = ModelUiModel(
                "gpt-3.5-turbo",
                "GPT-3.5 Turbo",
                "OpenAI",
            ),
//            messages = listOf(
//                ChatMessage("1", MessageRole.USER, "你好！"),
//                ChatMessage("2", MessageRole.ASSISTANT, "你好！有什么可以帮您的吗？"),
//                ChatMessage("3", MessageRole.USER, "请介绍一下你自己。"),
//                ChatMessage(
//                    "4",
//                    MessageRole.ASSISTANT,
//                    "我是一个基于 GPT-3.5 Turbo 模型的智能助手，可以帮助你解答问题、提供建议和进行对话。"
//                ),
//            ),
            availableModels = listOf(
                ModelUiModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI"),
                ModelUiModel("gpt-4", "GPT-4", "OpenAI"),
                ModelUiModel("custom-agent", "自定义 Agent", "本地部署")
            )
        )
    )
}

