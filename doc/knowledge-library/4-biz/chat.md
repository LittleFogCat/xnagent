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

### 2.2 智能体（公开 Identity + 本地智能体库）

**来源与合并**：服务端 `GET /api/chat/agents` 是「有哪些智能体」的权威来源，本地 `agent` 表（Room v3 新增）只承载对智能体的本地认知。`AgentRepositoryImpl` 合并后由 `StateFlow<List<AgentUiModel>>` 输出，`HomeViewModel` 与 `SettingsViewModel` 都订阅它（`HomeRepository.getAgents()` 已删除，`AgentRepository` 是唯一来源）。

`agent` 表一行 = 本地对某个智能体的认知，`id` 是服务端 `identity` id 或本地生成的 `local-<uuid>`。四种角色：

| 行 | 条件 | 合并行为 |
| --- | --- | --- |
| 本地自定义 | `isCustom = true` | 服务端不存在该 identity，附加在列表尾部 |
| 服务端覆盖 | 有本地行且字段被改过 | 本地字段覆盖服务端字段，`isOverridden = true` |
| 隐藏 | `isHidden = true` | 仅列表不展示（设置页「已隐藏的智能体」区可恢复），可传 `chatTarget` |
| 删除墓碑 | `isDeleted = true` | 把该服务端智能体从合并结果里剔除，避免下次 `refresh()` 后复活 |

- **只有被改动过的行才落库**：未编辑 / 未隐藏的服务端智能体不写本地，避免本地库沦为服务端清单的镜像。
- **`boundSessionId`（会话绑定）**：系统智能体从 `GET /api/chats` 的 `chatTarget.id` 反查（`refreshBindings()`，游客态该接口 401，失败时清空映射、下次成功拉取再填），权威且跨端；自定义智能体在远端没有 `chatTarget`，只能由 `bindSession()` 落本地。
- **同一用户对同一 `identity` 智能体只能保留一条绑定会话**（服务端限制，详见 `api/chat.md` 的「创建聊天记录」）。详情页的「添加对话」正是先查已存在的绑定会话直接打开，查不到才 `createChat`——重复 `POST /api/chats` 会返回 500。
- 抽屉展示：`HomeViewModel` 缓存合并结果到 `availableAgents`，会话条目按 `chatTarget.id` 或 `boundSessionId` 合并展示智能体名称 / 头像（当前仅展示首字母占位头像）。

### 2.3 提示词与人格来源（本地为准）

服务端 `/api/chat` 只有 `chatTarget`，**没有**提示词字段，所以本地提示词只能由客户端在请求体头部注入一条 `system` 消息（不落盘、不产生气泡）。`HomeViewModel.sendConversation` 的规则：

- **本地提示词非空** → 注入 `system` 消息，且**不传** `chatTarget`：否则服务端原人格会与本地提示词叠加成两套人格；
- **只改了名称 / 简介的系统智能体** → 继续传 `chatTarget`，保住服务端人格与权限计费（权限按模型 `chat:chat_free` / `chat:chat_paid` 校验，与 `chatTarget` 无关）；
- **自定义智能体**（`isCustom`，服务端不认识其 id）→ 永远不传 `chatTarget`，传了会被拒。

重新生成助手消息时同样会重新注入，因此提示词在编辑重发 / 重新生成路径下都生效。

### 2.4 智能体管理（设置页 + 详情页）

- 设置页「智能体」区：顶部固定「新建智能体」入口，下方按 `isHidden` 拆成 (可见, 已隐藏) 两组——可见组条目右侧在 `isBound` 时显示「已添加」徽标；已隐藏组只在非空时出现，提供「恢复」按钮（`agentRepository.setAgentHidden(id, false)`）。
- 点击条目 / 「新建智能体」进入**智能体详情页**（`MainDestination.AgentDetail(agentId)`，`agentId == null` 为新建），可编辑**名称（必填）/ 简介 / 提示词**，`role` 只读展示（需求只要这三个字段）。
- 详情页底部动作：保存 / 添加对话（已有绑定会话时文案为「打开已有对话」）/ 隐藏（或取消隐藏）/ 删除（`AlertDialog` 二次确认，删除成功后 `AgentDetailEvent.Closed` 退回设置页）。
- **添加 / 删除 / 隐藏三种语义**：添加 = 新建本地自定义智能体；隐藏 = 仅列表不展示、可恢复；删除 = 永久移除（服务端智能体写墓碑，自定义智能体直接删行）。已创建的历史会话不受影响，仍保留在会话列表中。

## 3. 会话管理

### 3.1 存储双源

- **远端**：`/api/chats*` 系列接口（`ChatApi`）；
- **本地**：`Session` + `ChatMessage` + `Agent`（`ChatDao` / `AgentDao`，`XNDatabase` v3；`exportSchema = true`，`app/schemas/…/N.json` 随代码提交。`fallbackToDestructiveMigration(dropAllTables = BuildConfig.DEBUG)` 只在 debug 包静默清库，release 包缺 Migration 时会直接崩溃）；
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

- 存储：`FavoriteRepositoryImpl` 使用 SharedPreferences（`favorite_store`）以 JSON 字符串持久化；写入时正文取 `content.ifBlank { reasoningContent }`，纯思考消息才有内容可存；
- 内存态：`StateFlow<List<FavoriteMessage>>`；
- UI 写入：消息长按菜单 → `HomeIntent.FavoriteMessage` → `favoriteRepository.addFavorite`；
- UI 展示：消息列表中已收藏消息的图标会高亮（`HomeUiState.favoriteMessageIds`）；设置页「我的收藏」区域展示完整列表；
- **条目交互（参考微信收藏）**：点击整行跳转到对应会话并高亮定位，长按在按压点弹出菜单（复制 / 删除收藏）；列表项**不再常驻删除按钮**；
- **跳转定位**：`SettingsViewModel.openFavorite(favorite)` → `SettingsEvent.OpenFavorite(sessionId, highlight)` → `MainViewModel.openChat(sessionId, highlight)` → `HomeUiState.pendingHighlight` → `HomeIntent.HighlightMessage` → `ChatMessageList` 滚动定位并闪烁两遍（定位规则与 `shouldFollowBottom` 的处理见 [`architecture.md` §9](../1-overview/architecture.md)）。收藏的 `sessionId` 可能为空（历史数据）或指向已删除的会话——前者在 `openFavorite` 里直接提示「该收藏未关联会话，无法跳转」，后者由主页在 `loadSession` 失败时提示「会话不存在或已删除」并清空高亮，不让界面停在空白；
- 级联清理：`HomeRepository.deleteSession` 在删除会话时调用 `removeFavoritesBySessionId`，避免孤儿收藏指向不存在的会话。

## 7. 设置页

- 智能体：见 §2.2 ~ §2.4（列表 → 详情页编辑 / 隐藏 / 删除，「添加对话」是详情页里的独立按钮）；
- 收藏：`SettingsRow` 展开后列出 `FavoriteRepository.favorites`（见 §6）；
- 跳转：设置页的两个跳转都由 `SettingsEvent` 上报给 `MainViewModel`（`OpenFavorite` → `openChat`；智能体详情由 `onOpenAgentDetail` → `openAgentDetail`）；
- 提示：`SettingsUiState.noticeMessage` 由 `SnackbarHostState` 弹出后立即 `consumeNotice()` 消费。设置页是可长滚动页面，提示若渲染在 `Column` 最底部用户根本看不到，观感等同于「点了没反应」——这正是「已添加的智能体点了没反应」被误判的成因之一；
- 「清除本地数据」会同时清空本地聊天、智能体、收藏与登录态（`SettingsViewModel.clearLocalData` 依次调用 `homeRepository.clearLocalChats()` / `agentRepository.clearAgents()` / `favoriteRepository.clearFavorites()` / `authRepository.logout()`，详见 [`./auth.md`](./auth.md) 的「登出」一节）。

## 8. 游客限制

- `HomeViewModel` 私有常量 `GUEST_USER_MESSAGE_LIMIT = 10`；
- `HomeUiState.isGuestMessageLimitReached = isGuest && guestUserMessageCount >= 10`；
- 达到上限后：发送按钮 disable，输入区上方显示提示条，提供「登录」/「注册」入口（跳转 `MainViewModel.openLogin`）。

## 9. 深度思考

- 入口：底部输入区 `OutlinedButton`（`Icons.Outlined.Lightbulb`）；
- 状态：`HomeUiState.isDeepThinkingEnabled`；
- 行为：发起请求时附带 `ThinkingConfig.Type.ENABLED / DISABLED` 到 `/api/chat`；
- 渲染：助手消息存在 `reasoningContent` 时显示折叠的「思考过程」区域（`ChatMessageList#ReasoningSection`），可手动展开。
