# 聊天业务

聊天业务是 XNAgent 的核心，包含模型选择、智能体、会话管理、SSE 流式对话、消息编辑 / 重新生成 / 删除 / 收藏等能力。完整接口定义见 [`../../api/chat.md`](../../api/chat.md)。

## 1. 模型与智能体

### 1.1 模型

- 来源：`GET /api/chat/models`，返回 `models` + `defaultModel`；
- `HomeViewModel.observeModels` 在初始化时拉取一次，**优先保留当前会话已选模型**，避免刷新时模型跳变；
- 本地兜底：`assets/model_config.json`（`HomeRepository.loadModelConfig`），仅在远端拉取失败时使用。

### 1.2 智能体（公开 Identity）

- 来源：`GET /api/chat/agents`；
- 使用场景：
  - `SettingsScreen` 中「智能体」区域列出全部可添加智能体；
  - `SettingsViewModel.addAgentToChat` 调用 `createChat` 创建绑定会话；
- 同一用户对同一 `identity` 智能体只能保留一条绑定会话（服务端限制，详见 `chat.md` 的「创建聊天记录」）。

## 2. 会话管理

### 2.1 存储双源

- **远端**：`/api/chats*` 系列接口（`ChatApi`）；
- **本地**：`Session` + `ChatMessage`（`ChatDao`，`XNDatabase` v1，启用 `fallbackToDestructiveMigration(dropAllTables = true)`）；
- **来源选择**：`HomeRepository.loadStoredChat(sessionId, useRemote)` 与 `saveStoredChat(...)` 接受 `useRemote` 标志，`HomeViewModel` 根据 `authRepository.session.value.isLoggedIn` 自动传入。

### 2.2 同步策略

`HomeRepository.syncLocalChatsToRemote()` 在登录后由 `HomeViewModel.observeAuthState` 调用：

```
遍历本地 Session
  ↓ 对每条
  POST /api/chats 创建远端会话（仅当消息非空）
  ↓ 成功
  本地删除该会话（消息 + session）
```

`clearLocalChats()` 仅清空本地（`chatDao.clearChatMessages()` + `clearSessions()`），不影响远端。

### 2.3 会话列表刷新

`HomeViewModel.applySessionList` 在会话列表刷新时：

1. 优先保留当前选中的 `sessionId`，找不到时回退到列表第一条；
2. 通过 `selected` 字段标记侧边栏选中态；
3. 若选中项变化或消息为空，触发 `loadSession`。

## 3. SSE 流式对话

### 3.1 发送流程

```
HomeIntent.SendMessage
  ↓
HomeViewModel.sendNewMessage
  1. 构造 ChatMessage(USER)，乐观写入 HomeUiState.messages
  2. sendConversation(baseMessages)
       ↓
     HomeRepository.saveStoredChat（落盘 base messages；远端模式下创建新会话或更新现有会话）
       ↓
     StreamChatApi.chat(@Streaming)  →  ResponseBody
       ↓
     HomeRepositoryImpl.sendToLLM 逐行解析 SSE
       ↓
     Flow<SendToLLMResult.{Thinking | Streaming | Error | Success}>
       ↓
     HomeViewModel 累积并 upsertAssistantMessage（保持同一条 ChatMessage）
       ↓
     完成后 HomeRepository.saveStoredChat 落盘最终消息
```

### 3.2 SSE 帧解析

`HomeRepositoryImpl.sendToLLM` 处理三类帧：

| 帧 | 含义 | UI 行为 |
| --- | --- | --- |
| `data: {"reasoningContent":"..."}` | 思考片段 | 累积到 `reasoningContent`，标记 `isThinking = true` |
| `data: {"content":"..."}` | 正文片段 | 累积到 `content`，关闭 `isThinking`，保持 `isGenerating = true` |
| `data: [DONE]` | 终态标记 | 跳出循环，结束流 |
| `:` 开头 | 注释 / 心跳 | 忽略 |

解析失败时仅 `Log.w`，不中断整次流（单条容错）。

### 3.3 折叠为同一条助手消息

`HomeViewModel.sendConversation` 中维护 `assistantMessageId`、`accumulatedContent`、`accumulatedReasoning`、`thinkingStartedAtMs`：

- reasoning 片段到达：`isThinking = true`，记录思考起始时间；
- 正文片段到达：`isThinking = false`，但 `isGenerating = true`；
- 终态：`isGenerating = false`；
- 每收到一段都通过 `upsertAssistantMessage` 重新构造**同一条** `ChatMessage` 替换列表中的最后一条，避免出现「思考 + 正文」两条占位项。

## 4. 消息动作

| Intent | 行为 | 持久化 |
| --- | --- | --- |
| `EditUserMessage(messageId, content)` | 从该用户消息开始截断，重新生成 | `sendConversation`（含落盘） |
| `RegenerateAssistantMessage(messageId)` | 找该助手消息前最近一条 USER，截断后重新生成 | 同上 |
| `DeleteMessage(messageId)` | 从列表中移除 | `persistConversation`（本地或远端更新） |
| `FavoriteMessage(messageId)` | 写入收藏 | `FavoriteRepository.addFavorite` |

> `EditUserMessage` 与 `RegenerateAssistantMessage` 都从「目标消息处截断」后调用同一 `sendConversation`，确保上下文一致。

## 5. 消息收藏

- 存储：`FavoriteRepositoryImpl` 使用 SharedPreferences（`favorite_store`）以 JSON 字符串持久化；
- 内存态：`StateFlow<List<FavoriteMessage>>`；
- UI 写入：消息长按菜单 → `HomeIntent.FavoriteMessage` → `favoriteRepository.addFavorite`；
- UI 展示：消息列表中已收藏消息的图标会高亮（`HomeUiState.favoriteMessageIds`）；设置页「我的收藏」区域展示完整列表并支持删除。

## 6. 智能体与收藏（设置页）

- 智能体：`SettingsViewModel.addAgentToChat` 调用 `createChat` 创建一个绑定该智能体的远端会话，成功后 UI 提示「已添加」；
- 收藏：设置页 `SettingsRow` 展开后列出 `FavoriteRepository.favorites`，每条支持「移除」；
- 「清除本地数据」会同时清空本地聊天、收藏与登录态（详见 [`./auth.md`](./auth.md) 的「登出」一节）。

## 7. 游客限制

- `HomeViewModel` 私有常量 `GUEST_USER_MESSAGE_LIMIT = 10`；
- `HomeUiState.isGuestMessageLimitReached = isGuest && guestUserMessageCount >= 10`；
- 达到上限后：发送按钮 disable，输入区上方显示提示条，提供「登录」/「注册」入口（跳转 `MainViewModel.openLogin`）。

## 8. 深度思考

- 入口：底部输入区 `OutlinedButton`（`Icons.Outlined.Lightbulb`）；
- 状态：`HomeUiState.isDeepThinkingEnabled`；
- 行为：发起请求时附带 `ThinkingConfig.Type.ENABLED / DISABLED` 到 `/api/chat`；
- 渲染：助手消息存在 `reasoningContent` 时显示折叠的「思考过程」区域（`ChatMessageList#ReasoningSection`），可手动展开。
