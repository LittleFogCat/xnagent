# 聊天业务

聊天业务是 XNAgent 的核心，包含模型选择、智能体、会话管理、SSE 流式对话、消息编辑 / 重新生成 / 删除 / 收藏等能力。完整接口定义见 [`../../api/chat.md`](../../api/chat.md)。

## 1. 会话标题

### 1.1 自动生成

普通会话标题由客户端在用户发出首条消息后，**串行**通过 LLM 提炼：

1. 用户在新建会话页输入文本并点击发送。
2. 客户端先把用户消息乐观写入本地 `messages`，并展示 `isGeneratingTitle = true` 的加载态。
3. 客户端调用 `HomeRepository.generateTitle(text, modelId)`，底层复用 `POST /api/chat` 流式接口发送：
   - `system`：「请把以下用户消息提炼为一个 8 到 15 字的简洁中文标题，不要使用标点符号、引号或额外说明，只输出标题本身。」
   - `user`：用户首条消息原文；
   - `thinking: disabled`，避免 `reasoning_content` 干扰标题输出。
4. 客户端解析 SSE 流，累积 `content` 字段，遇到 `[DONE]` 收尾。
5. 标题清洗：trim、剥离成对引号 / markdown 围栏、超过 15 字截断；空结果 fallback 为 `新对话`。
6. 调用 `POST /api/chats`（登录）或本地 `replaceSessionMessages`（游客）创建带标题的会话。
7. 会话创建成功后，才进入模型流式回复。

错误兜底：标题生成失败（含超时 / 解析失败 / LLM 拒绝）一律 fallback 为 `新对话`，会话照常创建，不阻塞后续对话。

**标题生成时机**：LLM 标题**仅**在新建会话并发送首条消息时生成一次。**编辑用户消息、重新生成助手消息、删除消息**等后续路径**不会**改写标题（partial update 语义：`saveStoredChat(sessionId=currentSessionId, title=null, ...)` 完全不传 title 字段）。需要修改标题请在抽屉条目长按菜单选「重命名」。

### 1.2 智能体会话

智能体会话始终使用智能体名称作为展示标题，跳过自动标题生成；UI 在详情页与抽屉条目中均展示智能体名称（详见 `5-ui/design-system.md` 与 `5-ui/navigation.md`）。

### 1.3 重命名

用户可在会话列表条目长按菜单中选择「重命名」手动改写标题（智能体不允许重命名，菜单项不显示）。重命名通过 `HomeRepository.renameSession` 完成：

- 本地：`ChatDao.updateSessionTitle` 同时刷新 `updateTime`，确保 `ORDER BY isPinned DESC, updateTime DESC` 正确重排；
- 远端：`PUT /api/chats/:id { title }`；
- 远端失败时入队 `PendingRetryQueue`，下次 `refreshRemoteSessions` 之前重试；失败仅记录日志，不回滚本地（乐观更新策略）。

## 2. 模型与智能体

### 2.1 模型

- 来源：`GET /api/chat/models`，返回 `models` + `defaultModel`；
- `HomeViewModel.observeModels` 在初始化时拉取一次，**优先保留当前会话已选模型**，避免刷新时模型跳变；
- 本地兜底：`assets/model_config.json`（`HomeRepository.loadModelConfig`），仅在远端拉取失败时使用。

### 2.2 智能体（公开 Identity）

- 来源：`GET /api/chat/agents`；
- 使用场景：
  - `SettingsScreen` 中「智能体」区域列出全部可添加智能体；
  - `SettingsViewModel.addAgentToChat` 调用 `createChat` 创建绑定会话；
- 同一用户对同一 `identity` 智能体只能保留一条绑定会话（服务端限制，详见 `chat.md` 的「创建聊天记录」）。
- 抽屉展示：`HomeViewModel.observeAgents` 缓存智能体列表到 `availableAgents`，会话条目按 `chatTarget.id` 合并展示智能体名称 / 头像（当前仅展示首字母占位头像）。

## 3. 会话管理

### 3.1 存储双源

- **远端**：`/api/chats*` 系列接口（`ChatApi`）；
- **本地**：`Session` + `ChatMessage`（`ChatDao`，`XNDatabase` v2，启用 `fallbackToDestructiveMigration(dropAllTables = true)`）；
- **来源选择**：`HomeRepository.loadStoredChat(sessionId, useRemote)` 与 `saveStoredChat(...)` 接受 `useRemote` 标志，`HomeViewModel` 根据 `authRepository.session.value.isLoggedIn` 自动传入。

### 3.2 同步策略

`HomeRepository.syncLocalChatsToRemote()` 在登录后由 `HomeViewModel.observeAuthState` 调用：

```
遍历本地 Session
  ↓ 对每条
  POST /api/chats 创建远端会话（仅当消息非空）
  ↓ 成功
  本地删除该会话（消息 + session）
```

`clearLocalChats()` 仅清空本地（`chatDao.clearChatMessages()` + `clearSessions()`），不影响远端。

### 3.3 会话列表刷新

`HomeViewModel.applySessionList` 在会话列表刷新时：

1. 优先保留当前选中的 `sessionId`，找不到时回退到列表第一条；
2. 通过 `selected` 字段标记侧边栏选中态；
3. 若选中项变化或消息为空，触发 `loadSession`。

### 3.4 会话置顶

- 存储：`Session.isPinned: Boolean = false`（Room v2 schema）；
- 排序：`ChatDao.querySessionList()` 用 `ORDER BY isPinned DESC, updateTime DESC`，置顶会话固定在最前；
- UI 分组：`HomeUiStateExt.partitionByPin()` 将 `SessionUiModel` 列表拆为 (pinned, normal)；置顶组仅在非空时显示「置顶」小标题；
- 持久化：`HomeRepository.pinSession(sessionId, isPinned, useRemote)` 同时落本地（`updateSessionPinned`）与远端（`PUT /api/chats/:id { isPinned }`），远端失败仅记录日志；
- 「远端无字段时本地为准」：`HomeViewModel.refreshRemoteSessions` 通过 `HomeRepository.getLocalPinnedSessionIds()` 取本地集合，远端 `isPinned` 为 `null` 时回退到本地值。

### 3.5 会话删除

`HomeRepository.deleteSession(sessionId, useRemote)`：

1. 本地事务删除：`ChatDao.deleteSessionWithMessages` 同时清理消息与会话，避免出现孤儿消息；
2. 远端：`DELETE /api/chats/:id`；
3. 级联收藏：`FavoriteRepository.removeFavoritesBySessionId(sessionId)` 移除该会话下的全部收藏。

## 4. SSE 流式对话

### 4.1 发送流程

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

### 4.2 SSE 帧解析

`HomeRepositoryImpl.sendToLLM` 处理三类帧：

| 帧 | 含义 | UI 行为 |
| --- | --- | --- |
| `data: {"reasoningContent":"..."}` | 思考片段 | 累积到 `reasoningContent`，标记 `isThinking = true` |
| `data: {"content":"..."}` | 正文片段 | 累积到 `content`，关闭 `isThinking`，保持 `isGenerating = true` |
| `data: [DONE]` | 终态标记 | 跳出循环，结束流 |
| `:` 开头 | 注释 / 心跳 | 忽略 |

解析失败时仅 `Log.w`，不中断整次流（单条容错）。

### 4.3 折叠为同一条助手消息

`HomeViewModel.sendConversation` 中维护 `assistantMessageId`、`accumulatedContent`、`accumulatedReasoning`、`thinkingStartedAtMs`：

- reasoning 片段到达：`isThinking = true`，记录思考起始时间；
- 正文片段到达：`isThinking = false`，但 `isGenerating = true`；
- 终态：`isGenerating = false`；
- 每收到一段都通过 `upsertAssistantMessage` 重新构造**同一条** `ChatMessage` 替换列表中的最后一条，避免出现「思考 + 正文」两条占位项。

## 5. 消息动作

| Intent | 行为 | 持久化 |
| --- | --- | --- |
| `EditUserMessage(messageId, content)` | 从该用户消息开始截断，重新生成 | `sendConversation`（含落盘） |
| `RegenerateAssistantMessage(messageId)` | 找该助手消息前最近一条 USER，截断后重新生成 | 同上 |
| `DeleteMessage(messageId)` | 从列表中移除 | `persistConversation`（本地或远端更新） |
| `FavoriteMessage(messageId)` | 写入收藏 | `FavoriteRepository.addFavorite` |
| `SetSessionPinned(sessionId, pin)` | 切换置顶状态 | `HomeRepository.pinSession`（双写） |
| `DeleteSession(sessionId)` | 删除整条会话 | `HomeRepository.deleteSession`（含级联收藏） |
| `RenameSession(sessionId, newTitle)` | 重命名会话（智能体不允许） | `HomeRepository.renameSession`（双写） |

> `EditUserMessage` 与 `RegenerateAssistantMessage` 都从「目标消息处截断」后调用同一 `sendConversation`，确保上下文一致。

## 6. 消息收藏

- 存储：`FavoriteRepositoryImpl` 使用 SharedPreferences（`favorite_store`）以 JSON 字符串持久化；
- 内存态：`StateFlow<List<FavoriteMessage>>`；
- UI 写入：消息长按菜单 → `HomeIntent.FavoriteMessage` → `favoriteRepository.addFavorite`；
- UI 展示：消息列表中已收藏消息的图标会高亮（`HomeUiState.favoriteMessageIds`）；设置页「我的收藏」区域展示完整列表并支持删除；
- 级联清理：`HomeRepository.deleteSession` 在删除会话时调用 `removeFavoritesBySessionId`，避免孤儿收藏指向不存在的会话。

## 7. 智能体与收藏（设置页）

- 智能体：`SettingsViewModel.addAgentToChat` 调用 `createChat` 创建一个绑定该智能体的远端会话，成功后 UI 提示「已添加」；
- 收藏：设置页 `SettingsRow` 展开后列出 `FavoriteRepository.favorites`，每条支持「移除」；
- 「清除本地数据」会同时清空本地聊天、收藏与登录态（详见 [`./auth.md`](./auth.md) 的「登出」一节）。

## 8. 游客限制

- `HomeViewModel` 私有常量 `GUEST_USER_MESSAGE_LIMIT = 10`；
- `HomeUiState.isGuestMessageLimitReached = isGuest && guestUserMessageCount >= 10`；
- 达到上限后：发送按钮 disable，输入区上方显示提示条，提供「登录」/「注册」入口（跳转 `MainViewModel.openLogin`）。

## 9. 深度思考

- 入口：底部输入区 `OutlinedButton`（`Icons.Outlined.Lightbulb`）；
- 状态：`HomeUiState.isDeepThinkingEnabled`；
- 行为：发起请求时附带 `ThinkingConfig.Type.ENABLED / DISABLED` 到 `/api/chat`；
- 渲染：助手消息存在 `reasoningContent` 时显示折叠的「思考过程」区域（`ChatMessageList#ReasoningSection`），可手动展开。
