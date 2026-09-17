# 架构说明

## 项目定位

XNAgent 是一款基于 Android 平台的 AI 智能体聊天客户端。核心能力包括：

- **多模型对话**：支持多种 LLM 模型的流式（SSE）聊天；
- **公开智能体**：服务端维护一组带人格提示词的智能体（Identity），用户可直接发起绑定会话；
- **认证体系**：邮箱密码 + 邮箱验证注册 + 游客体验；登录态采用 v2 双 token + 设备绑定，支持 Token Rotation；
- **会话管理**：支持历史会话、消息编辑 / 重新生成 / 删除 / 收藏，本地与远端双写；
- **深度思考**：可按会话开启「深度思考」开关，让模型先输出 reasoning 再输出正文。

## 技术栈一览

| 维度 | 选型 | 备注 |
| --- | --- | --- |
| 语言 | Kotlin **2.2.10** | 全局启用 kotlinx.serialization 插件 |
| UI | Jetpack Compose（Material3，Compose BOM **2026.02.01**） | 启用动态取色（Android 12+） |
| 构建 | AGP **9.1.1** | `compileSdk` 须使用 AGP 9.x DSL |
| 依赖注入 | Hilt **2.59.2** + KSP | Hilt 模块集中在 `App.kt` |
| 持久化 | Room **2.8.3** | `XNDatabase`（v3）：`exportSchema = true` + `room.schemaLocation = $projectDir/schemas`，生成的 `app/schemas/…/N.json` 随代码提交；手工迁移集中在 `Migrations.kt`。`fallbackToDestructiveMigration(dropAllTables = BuildConfig.DEBUG)` 只在 debug 包静默清库，release 包缺 Migration 时会直接崩溃 |
| 网络 | Retrofit **2.9.0** + OkHttp **4.11.0** + kotlinx.serialization | SSE 单独走专用 OkHttp 客户端 |
| Markdown | Markwon **4.6.2** | 助手消息正文使用 |
| 协程 | kotlinx.coroutines | 调试期打开 `kotlinx.coroutines.debug` |

详细版本与选型理由见 [tech-stack.md](./tech-stack.md)。

## 整体架构

项目采用 **单 Activity + Compose + MVI-like** 的架构：唯一 `MainActivity` 根据 `MainViewModel.uiState.destination` 路由到四个顶级页面（`HomeScreen`、`SettingsScreen`、`AgentDetailScreen`、`LoginScreen`），各页面内部再以 Intent / State / ViewModel 三段式驱动 UI。

### 分层

```
┌──────────────────────────────────────────────────────────────┐
│  UI 层（Compose）                                            │
│  - Screen（无 Hilt 依赖的 HomeScreenContent / LoginContent） │
│  - Component（ChatMessageList / ChatInputBar / Dropdown…）   │
│  - Model（HomeUiState / ChatMessage / ModelUiModel…）        │
└────────────────────────────┬─────────────────────────────────┘
                             │  订阅 StateFlow
┌────────────────────────────▼─────────────────────────────────┐
│  ViewModel 层（MVI-like）                                    │
│  - MainViewModel / HomeViewModel / LoginViewModel /          │
│    SettingsViewModel / AgentDetailViewModel                  │
│  - Intent 入口（HomeIntent / LoginIntent /                   │
│    AgentDetailIntent）                                       │
└────────────────────────────┬─────────────────────────────────┘
                             │  调用仓库方法
┌────────────────────────────▼─────────────────────────────────┐
│  Repository 层                                               │
│  - HomeRepository / AuthRepository / FavoriteRepository /    │
│    AgentRepository                                           │
│  - 统一远端 API（Retrofit）与本地存储（Room / SharedPrefs）   │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────┬───────────────┼──────────────┬─────────────────┐
│            │               │              │                 │
▼            ▼               ▼              ▼                 ▼
Remote     Local DB         Local KV     SSE Stream       SharedPrefs
(Retrofit) (Room)         (Assets)    (OkHttp 直读)      (Auth/收藏)
```

### 关键设计

#### 1. 唯一 Activity + 状态驱动路由

`MainActivity` 通过 `setContent { when (destination) { … } }` 在四个顶级页面间切换（`uiState` 是委托属性，先取出 `val destination = uiState.destination` 再 `when`，否则分支内的智能转换不可用）。`MainDestination` 是 sealed interface（`Home` / `Settings` / `Login` / `AgentDetail(agentId)`），由 `MainViewModel` 根据 `AuthSession.canEnterHome` 自动重定向：

- 未登录或认证态失效 → `Login`；
- 已登录或游客 → `Home`；
- `Settings` 由用户从抽屉进入；
- `AgentDetail(agentId)` 由设置页智能体条目 / 「新建智能体」进入，`agentId == null` 表示新建，返回走 `openSettings()` 回到设置页。

#### 2. MVI-like 模式

每个 Screen 对应一个 ViewModel，ViewModel 暴露 `dispatch(Intent)` 入口和 `StateFlow<UiState>` 出口。Screen 是纯 Composable（`HomeScreenContent` / `LoginContent` / `AgentDetailContent`），不依赖 Hilt，方便 Compose Preview。

页面级副作用（跳转、返回）通过 `MutableSharedFlow<XxxEvent>`（`extraBufferCapacity = 1`，`tryEmit`）上报给上层路由，而不是塞进 UiState。

Intent 集合：

- `HomeIntent`：`Initialize` / `SetAgentMode` / `UpdateInput` / `SendMessage` / `CancelMessage` / `SelectModel` / `SelectSession` / `CreateNewChat` / `ToggleDeepThinking` / `EditUserMessage` / `RegenerateAssistantMessage` / `DeleteMessage` / `FavoriteMessage` / `SetSessionPinned` / `DeleteSession` / `RenameSession` / `ClearConversation` / `HighlightMessage(highlight)` / `ConsumeHighlight` / `ConsumeError`。会话管理扩展：`SetSessionPinned(sessionId, pin)` 切换置顶；`DeleteSession(sessionId)` 整条删除；`RenameSession(sessionId, newTitle)` 重命名（智能体会话由 UI 层屏蔽）。`HighlightMessage` 承接收藏跳转的定位信息，闪烁完成后由 UI 派发 `ConsumeHighlight` 复位；`SelectSession` / `CreateNewChat` 也会主动清空未消费的高亮。
- `LoginIntent`：`UpdateEmail` / `UpdatePassword` / `UpdateCaptchaAnswer` / `UpdateVerificationCode` / `ToggleMode` / `RefreshCaptcha` / `Login` / `RequestRegisterCode` / `CompleteRegister` / `ContinueAsGuest`。
- `AgentDetailIntent`：`Load(agentId)` / `UpdateName` / `UpdateDescription` / `UpdatePrompt` / `Save` / `OpenChat` / `SetHidden(hidden)` / `Delete` / `ConsumeNotice`。

#### 3. 网络层双客户端

`App.kt#NetworkModule` 同时提供两个 OkHttp + Retrofit：

- **普通客户端**（`provideOkHttpClient` + `provideNormalRetrofit`）：附带 Bearer Token、401 自动刷新、`HttpLoggingInterceptor(BODY)`；
- **SSE 专用客户端**（`@Named("sse")`）：无 `HttpLoggingInterceptor(BODY)`，否则会把响应体一次性读到内存，破坏流式消费；改用 `HttpStreamingLoggingInterceptor`。

`StreamChatApi.chat()` 使用 `@Streaming` 注解返回原始 `ResponseBody`，由 `HomeRepositoryImpl` 逐行解析 `data: {…}` 与 `data: [DONE]` 帧。

#### 4. 401 自动刷新 + Token Rotation

`TokenRefreshHandler` 使用 `ReentrantLock + Condition` 保证并发场景下只发一个 `/api/refresh` 请求，其余请求等待。`AuthStore.updateTokens` 单纯替换 token 字段，保留用户身份。`logout()` 时通过 `reset()` 唤醒等待中的线程。

#### 5. 双源会话存储

聊天会话与消息同时支持本地 Room 与远端 API：

- **本地**：`Session` + `ChatMessage` 表，`ChatDao.replaceSessionMessages` 在事务中替换整个会话；
- **远端**：`/api/chats*` 系列接口；
- **同步**：`HomeRepositoryImpl.syncLocalChatsToRemote()` 在登录后把游客期或离线期本地会话逐条补传到远端。

`loadStoredChat(sessionId, useRemote)` 与 `saveStoredChat(...)` 暴露统一接口，ViewModel 根据 `AuthSession.isLoggedIn` 自动选择 `useRemote` 路径。

`HomeRepository` 会话管理扩展（v2 起）：

- `generateTitle(firstUserMessage, modelId): String`：用 LLM 把首条用户消息提炼为 8~15 字标题（`suspend` 返回值，避免 `.first()` 契约脆弱）。
- `pinSession(sessionId, isPinned, useRemote)`：切换置顶状态，本地优先落盘，远端失败仅记录日志。
- `renameSession(sessionId, newTitle, useRemote)`：重命名会话，本地同时刷新 `updateTime`；空标题由调用方校验。
- `deleteSession(sessionId, useRemote)`：本地事务删除消息与会话（`ChatDao.deleteSessionWithMessages`），远端调用 `DELETE /api/chats/:id`；**联动**调用 `FavoriteRepository.removeFavoritesBySessionId` 清理孤儿收藏。
- `getLocalPinnedSessionIds(): Set<String>`：返回本地 Room 已置顶的会话 ID 集合，供 `refreshRemoteSessions` 在远端 `isPinned` 为 `null` 时回退到本地值。

`FavoriteRepository` 扩展：

- `removeFavoritesBySessionId(sessionId)`：级联删除指定会话下的全部收藏，用于 `HomeRepository.deleteSession` 联动清理，避免出现指向不存在会话的孤儿收藏。

`HomeUiState` / `SessionUiModel` / `AgentUiModel` 字段：

- `HomeUiState` 新增：`availableAgents: List<AgentUiModel>`（已缓存的智能体清单）；`isGeneratingTitle: Boolean`（LLM 标题生成中的中间态，UI 用 `CircularProgressIndicator` 提示）；`highlight: MessageHighlight?`（待定位闪烁的消息，消费后置 null）。
- `SessionUiModel` 新增：`isPinned: Boolean`（置顶标记）、`agentId: String?` / `agentName: String?` / `agentAvatarUrl: String?`（智能体展示字段，由 `chatTarget` + 已缓存的 `AgentUiModel` 合并而来）、`updatedAt: Long`（按其倒序展示）；派生 `isAgent: Boolean` / `displayTitle: String`。
- `AgentUiModel`：`id` / `name` / `avatarUrl`，加上本地管理所需的 `role` / `description` / `prompt` / `isCustom` / `isOverridden` / `boundSessionId` / `isHidden` / `isDeleted`（**新字段全部带默认值**，否则既有 `@Preview` 构造点会编译失败）；派生 `isBound`。
- `MessageHighlight`（新增，`ui/model/`）：`messageId` / `role` / `content`，收藏跳转的定位信息。

#### 6. 本地智能体库（`AgentRepository` + `agent` 表）

`agent` 表（Room v3 新增）一行 = 「本地对某个智能体的认知」，承载四种角色：本地自定义定义、服务端智能体覆盖、隐藏标记、删除墓碑，另外挂一个会话绑定。只有「自定义」和「字段被改过 / 被隐藏 / 被删除」的行会落库，服务端清单本身不落库，避免本地库沦为服务端镜像。

`AgentRepositoryImpl` 把 `GET /api/chat/agents`（权威来源：有哪些智能体）与本地行合并后写入 `StateFlow<List<AgentUiModel>>`：

- 本地 `isDeleted` 行是墓碑 → 把该服务端智能体从合并结果里剔除，避免刷新后复活；
- 本地行的字段覆盖服务端字段，并置 `isOverridden = true`；
- 本地 `isCustom` 行是纯本地智能体，附加在列表尾部；
- `boundSessionId`：服务端智能体由 `refreshBindings()` 从 `GET /api/chats` 的 `chatTarget.id` 反查（游客态该接口 401，失败时清空映射等待下次成功拉取）；自定义智能体取本地行字段，由 `bindSession()` 在创建会话后写入。

`agents` 是一个手工 `recompute()` 的 `MutableStateFlow`（而不是 `stateIn`），这样 `getAgent` / `findBySession` 能同步读到当前值。`HomeRepository.getAgents()` 已删除，`AgentRepository` 是唯一的智能体来源。

#### 7. 智能体人格来源（本地为准）

`/api/chat` 只有 `chatTarget`，没有提示词字段，所以本地提示词只能由客户端在请求体头部注入一条 `system` 消息（不落盘、不产生气泡）。`HomeViewModel.sendConversation` 的规则：

- 本地提示词非空 → 注入 `system` 消息，并且**不传** `chatTarget`（否则服务端原人格会与本地提示词叠加成两套人格）；
- 只改了名称 / 简介的系统智能体 → 继续传 `chatTarget`，保住服务端人格与权限计费；
- 自定义智能体（服务端不认识其 id）→ 永远不传 `chatTarget`。

#### 8. SSE 流式响应在 UI 上折叠为同一条助手消息

`HomeViewModel.sendConversation` 在收到 reasoning 片段时累积 `accumulatedReasoning`、收到正文片段时累积 `accumulatedContent`，并通过 `upsertAssistantMessage` 反复更新列表中**同一条**助手消息。这避免了「思考一段 + 正文一段」出现两条占位消息。

#### 9. 收藏跳转的高亮定位

`SettingsEvent.OpenFavorite(sessionId, highlight)` → `MainViewModel.openChat(sessionId, highlight)` → `HomeUiState.pendingHighlight` → `HomeScreen` 派发 `HomeIntent.HighlightMessage` → `ChatMessageList` 的副作用：

1. 等 `messages` 非空后定位目标：先按 `messageId`，远端消息 id 每次加载都会按内容重算，因此再用 `role + content.ifBlank { reasoningContent }` 兜底；
2. 定位不到（消息已删 / 会话被换）直接消费，避免残留状态在下个会话误闪；
3. `reverseLayout = true` + `messages.asReversed()` 下 `lazyIndex = (typingIndicator ? 1 : 0) + (messages.lastIndex - targetIndex)`，其中 `isResponding` 且没有活跃助手气泡时列表头部会多一个 `typing_indicator` item；
4. `animateScrollToItem` 后用一个 `Animatable` 跑两轮 亮(0.35) → 灭，把 alpha 传给命中的 `ChatMessageItem`，它在气泡底色之上叠一层 `primary` 半透明覆盖；
5. 结束后 `onHighlightConsumed()` → `HomeIntent.ConsumeHighlight` 复位。

定位期间置 `shouldFollowBottom = false`，并且「自动贴底」副作用通过 `rememberUpdatedState` 读取 `highlight`（而不是把它作为 effect 的 key），否则消费高亮会再次把列表拽到底部、把用户甩离刚跳到的历史消息。

## 数据流概览

### 登录态

```
LoginViewModel → AuthRepository.login(...) → AuthApi.loginV2 → AuthStore.saveSession
                                                                       ↓
                                                              StateFlow<AuthSession>
                                                                       ↓
                                       MainViewModel / HomeViewModel 订阅
```

### 发送消息（SSE）

```
用户输入 → HomeIntent.SendMessage
              ↓
HomeViewModel.sendNewMessage
              ↓
HomeRepository.saveStoredChat（落盘用户消息 + 创建/更新会话）
              ↓
HomeViewModel.sendConversation
  ├─ try 内：_uiState.update { isResponding = true }
  ├─ StreamChatApi.chat（@Streaming）  →  OkHttp 读取字节流
  ├─ HomeRepositoryImpl 解析 SSE 帧（reasoning / content）
  ├─ Flow<SendToLLMResult> → HomeViewModel 累积并刷新最后一条助手消息
  └─ finally：_uiState.update { isResponding = false }
```

`HomeUiState.isResponding` 表示「请求正在等待或接收 SSE 响应」，由 `sendConversation` 在 `try` 入口置 `true`，`finally` 中复位为 `false`，保证任何退出路径（`Success` / `Error` / 协程取消 / 兜底收尾）下 UI 都能解锁。UI 层据此：

- 禁用输入区发送按钮（`HomeScreen`）；
- 在 `ChatMessageList` 顶部插入 `TypingIndicator`（仅在没有正在增长的助手气泡时）。

### 收藏

```
用户长按 → HomeIntent.FavoriteMessage
              ↓
HomeViewModel.favoriteMessage
              ↓
FavoriteRepository.addFavorite（SharedPreferences JSON 持久化）
              ↓
StateFlow<List<FavoriteMessage>> → HomeUiState.favoriteMessageIds
                                   → SettingsViewModel 收藏列表
                                            ↓ 点击条目
                          SettingsEvent.OpenFavorite(sessionId, highlight)
                                            ↓
                          MainViewModel.openChat（切回 Home + 待选会话 + 高亮）
```

## 关键目录

```
app/src/main/java/tech/xiaoniu/xnagent/
├── App.kt                  # Hilt 入口 + AppModule/NetworkModule/DataModule
├── MainActivity.kt         # 唯一 Activity，按 destination 路由
├── MainViewModel.kt        # 根路由 ViewModel
├── data/                   # 数据层（API / DTO / Repository / 本地存储）
├── ui/
│   ├── component/          # 复用 Composable（ChatMessageList / ChatInputBar…）
│   ├── model/              # UI 模型（HomeUiState / ChatMessage / SessionUiModel）
│   ├── screen/
│   │   ├── agent/          # AgentDetailScreen + AgentDetailViewModel + AgentDetailIntent
│   │   ├── home/           # HomeScreen + HomeViewModel + HomeIntent
│   │   ├── login/          # LoginScreen + LoginViewModel + LoginIntent
│   │   └── settings/       # SettingsScreen + SettingsViewModel
│   └── theme/              # Color / Type / Theme
└── common/util/            # 通用工具（DateUtil）
```

详细目录与文件放置规则见 [`../2-convention/project-structure.md`](../2-convention/project-structure.md)。

