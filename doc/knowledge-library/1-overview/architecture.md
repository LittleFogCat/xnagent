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
| 持久化 | Room **2.8.3** | Schema 存在且启用 |
| 网络 | Retrofit **2.9.0** + OkHttp **4.11.0** + kotlinx.serialization | SSE 单独走专用 OkHttp 客户端 |
| Markdown | Markwon **4.6.2** | 助手消息正文使用 |
| 协程 | kotlinx.coroutines | 调试期打开 `kotlinx.coroutines.debug` |

详细版本与选型理由见 [tech-stack.md](./tech-stack.md)。

## 整体架构

项目采用 **单 Activity + Compose + MVI-like** 的架构：唯一 `MainActivity` 根据 `MainViewModel.uiState.destination` 路由到三个顶级页面（`HomeScreen`、`SettingsScreen`、`LoginScreen`），各页面内部再以 Intent / State / ViewModel 三段式驱动 UI。

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
│    SettingsViewModel                                         │
│  - Intent 入口（HomeIntent / LoginIntent）                   │
└────────────────────────────┬─────────────────────────────────┘
                             │  调用仓库方法
┌────────────────────────────▼─────────────────────────────────┐
│  Repository 层                                               │
│  - HomeRepository / AuthRepository / FavoriteRepository      │
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

`MainActivity` 通过 `setContent { when (uiState.destination) { … } }` 在三个顶级页面间切换。`MainDestination` 是 sealed interface（`Home` / `Settings` / `Login`），由 `MainViewModel` 根据 `AuthSession.canEnterHome` 自动重定向：

- 未登录或认证态失效 → `Login`；
- 已登录或游客 → `Home`；
- `Settings` 由用户从抽屉进入。

#### 2. MVI-like 模式

每个 Screen 对应一个 ViewModel，ViewModel 暴露 `dispatch(Intent)` 入口和 `StateFlow<UiState>` 出口。Screen 是纯 Composable（`HomeScreenContent` / `LoginContent`），不依赖 Hilt，方便 Compose Preview。

Intent 集合：

- `HomeIntent`：`Initialize` / `SetAgentMode` / `UpdateInput` / `SendMessage` / `SelectModel` / `SelectSession` / `CreateNewChat` / `ToggleDeepThinking` / `EditUserMessage` / `RegenerateAssistantMessage` / `DeleteMessage` / `FavoriteMessage`。
- `LoginIntent`：`UpdateEmail` / `UpdatePassword` / `UpdateCaptchaAnswer` / `UpdateVerificationCode` / `ToggleMode` / `RefreshCaptcha` / `Login` / `RequestRegisterCode` / `CompleteRegister` / `ContinueAsGuest`。

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

#### 6. SSE 流式响应在 UI 上折叠为同一条助手消息

`HomeViewModel.sendConversation` 在收到 reasoning 片段时累积 `accumulatedReasoning`、收到正文片段时累积 `accumulatedContent`，并通过 `upsertAssistantMessage` 反复更新列表中**同一条**助手消息。这避免了「思考一段 + 正文一段」出现两条占位消息。

#### 7. 重复的 HomeRepository（注意）

`data/repository/HomeRepository` 是 **Active** 实现，被 `HomeViewModel` 注入；`ui/screen/home/HomeRepository` 是 **Stale** 副本，与 Active 内容一致但不再使用。新增方法时 **只改 Active**，Stale 副本建议在适当时机删除。

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
              ↓
StreamChatApi.chat（@Streaming）  →  OkHttp 读取字节流
              ↓
HomeRepositoryImpl 解析 SSE 帧（reasoning / content）
              ↓
Flow<SendToLLMResult> → HomeViewModel 累积并刷新最后一条助手消息
```

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
│   │   ├── home/           # HomeScreen + HomeViewModel + HomeIntent
│   │   ├── login/          # LoginScreen + LoginViewModel + LoginIntent
│   │   └── settings/       # SettingsScreen + SettingsViewModel
│   └── theme/              # Color / Type / Theme
└── common/util/            # 通用工具（DateUtil）
```

详细目录与文件放置规则见 [`../2-convention/project-structure.md`](../2-convention/project-structure.md)。

