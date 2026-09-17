# 导航

XNAgent 使用 **唯一 Activity + 状态驱动路由**，未引入 Jetpack Navigation 库。所有路由由 `MainViewModel.uiState.destination` 决定。

## 1. 顶级 Destination

`MainDestination`（`MainViewModel.kt`）：

```kotlin
sealed interface MainDestination {
    data object Home : MainDestination
    data object Settings : MainDestination
    data object Login : MainDestination

    /** agentId 为 null 表示新建自定义智能体。 */
    data class AgentDetail(val agentId: String?) : MainDestination
}
```

`MainActivity` 中：

```kotlin
// uiState 是 `by` 委托属性，先取出目标再 when，否则分支内的智能转换不可用。
val destination = uiState.destination
when (destination) {
    MainDestination.Home          -> HomeScreen(...)
    MainDestination.Settings      -> SettingsScreen(...)
    MainDestination.Login         -> LoginScreen(...)
    is MainDestination.AgentDetail -> AgentDetailScreen(agentId = destination.agentId, ...)
}
```

`AgentDetail` 带参数，所以 `AgentDetailScreen` 里用 `LaunchedEffect(agentId) { viewModel.dispatch(AgentDetailIntent.Load(agentId)) }` 显式重置表单：`hiltViewModel()` 的 owner 是 Activity，同一个 `AgentDetailViewModel` 实例会在不同 `agentId` 之间复用，不重置会把上一个智能体的编辑内容串到下一个。

## 2. 状态机

```
                            ┌──────────────────────────────┐
                            │                              │
                            ▼                              │
        ┌──────────────  MainDestination.Login   <─────────┤
        │  (未登录 / 注册中)                        │       │
        │                                          │  认证态变化
        │   登录成功 / 游客登录                     │       │
        ▼                                          │       │
MainDestination.Home  ───────────────────────────►│       │
        │                                          │       │
        │   抽屉「设置」                            │       │
        ▼                                          │       │
MainDestination.Settings                          │       │
        │        ▲                                 │       │
        │        │ 智能体条目 / 新建智能体          │       │
        │        │ （返回走 openSettings）          │       │
        ▼        │                                 │       │
MainDestination.AgentDetail                       │       │
        │                                          │       │
        │   返回 / 退出登录 / 清除本地数据          │       │
        └──────────────────────────────────────────┘       │
```

`MainViewModel` 行为：

- 初始：根据 `authRepository.session.value.canEnterHome` 决定 `Home` 或 `Login`；
- 订阅 `authRepository.session`：
  - `!canEnterHome` → `Login`；
  - 当前为 `Login` 且 `canEnterHome` → `Home`；
  - 其余情况保留 `destination`（保留从 Home 进入 Settings / AgentDetail 的状态）；
- `openHome` / `openSettings` / `openLogin` / `openAgentDetail(agentId)` / `openChat(sessionId, highlight)` / `logout` 提供显式切换；
- `openAgentDetail` 与 `openSettings` 一样带 `canEnterHome` 守卫，认证态失效时统一落到 `Login`；
- `openChat` 校验 `sessionId` 非空，把 `sessionId` 与可选的 `highlight` 分别写进 `pendingSessionId` / `pendingHighlight`，由 `HomeScreen` 消费后调 `consumePendingSessionId()` 一起清空。

## 3. 顶级页面之间的跳转

| 触发 | 来源 | 行为 |
| --- | --- | --- |
| 启动 | `MainActivity.onCreate` | 根据认证态路由 |
| 抽屉 → 设置 | `HomeScreen.DrawerContent.onOpenSettings` | `viewModel::openSettings` |
| 抽屉 → 登录 | `HomeScreen.DrawerContent.onOpenLogin` | `viewModel::openLogin` |
| 抽屉 → 新对话 | `HomeScreen.DrawerContent.onNewChat` | `HomeIntent.CreateNewChat`（仍在 Home） |
| 游客达到消息上限 | `HomeScreen` 顶部提示 | `onOpenLogin` → `openLogin` |
| 设置 → 返回 | `SettingsScreen` 顶栏返回 | `onBack = viewModel::openHome` |
| 设置 → 退出 / 登录 | `SettingsScreen` 底部 | 已登录：弹窗确认后调用 `viewModel.logout()`；游客：调用 `viewModel.openLogin()` |
| 设置 → 智能体详情 | `SettingsScreen.AgentItem` 点击 / `AgentCreateItem` | `onOpenAgentDetail(agent.id)` / `onOpenAgentDetail(null)` → `MainViewModel.openAgentDetail` |
| 智能体详情 → 返回 | `AgentDetailScreen` 顶栏返回、删除成功后 | `onBack = viewModel::openSettings`（回设置页而不是首页） |
| 智能体详情 → 聊天 | `AgentDetailScreen` 「添加对话 / 打开已有对话」 | `AgentDetailEvent.OpenChat(sessionId)` → `MainViewModel.openChat` 切回首页并携带 `pendingSessionId`；`HomeScreen` 在 `initialSessionId` 非空时派发 `SelectSession` 后调用 `consumePendingSessionId` 复位 |
| 收藏 → 会话 | `SettingsScreen.FavoriteItem` 点击 | `SettingsViewModel.openFavorite(favorite)` 发出 `SettingsEvent.OpenFavorite(sessionId, highlight)` → `MainViewModel.openChat(sessionId, highlight)`；`HomeScreen` 依次派发 `SelectSession` 与 `HighlightMessage` 再复位，`ChatMessageList` 滚动定位并闪烁两遍 |
| 登录 → 返回 | `LoginScreen`（已登录状态下显示） | `onBack = viewModel::openHome` |
| 登录 → 游客 | `LoginScreen` 顶栏 | `LoginIntent.ContinueAsGuest` |

## 4. 内部导航

### 4.1 首页抽屉（ModalNavigationDrawer）

`HomeScreen.HomeScreenContent` 使用 `ModalNavigationDrawer`，抽屉内容 `DrawerContent` 自上而下：

1. 标题「聊天记录」+「新对话」按钮；
2. 会话列表按置顶分组：`HomeUiStateExt.partitionByPin()` 把 `SessionUiModel` 拆为 `(pinned, normal)` 两组：
   - 置顶组仅在 `pinned.isNotEmpty()` 时渲染「置顶」小标题（`labelMedium`）；
   - 普通组不显示小标题，直接列条目；
   - 组内各自按 `updatedAt DESC` 排序，DAO 层 `ORDER BY isPinned DESC, updateTime DESC` 已保证总序。
3. 条目右侧附智能体头像（智能体会话）或无头像（普通会话），标题为 `displayTitle`（智能体取 `agentName`，否则取 `title`）；
4. 条目长按弹出下拉菜单（重命名 / 置顶切换 / 删除，智能体不显示重命名）。**菜单视觉风格按 [design-system.md §6.7](./design-system.md#67-弹窗--菜单ios-popover-风格)（iOS Popover）；菜单左上角锚到按压点（Popup anchor 嵌入 0 大小 offset Box）**；
5. 底部用户信息区：
   - 已登录：显示昵称、邮箱、「设置」按钮；
   - 游客：显示「游客」+「前往登录」按钮。

### 4.2 消息长按菜单

`ChatMessageList.ChatMessageItem` 长按触发菜单：

| 选项 | 用户消息 | 助手消息 |
| --- | --- | --- |
| 复制 | ✅ | ✅ |
| 修改并重发 | ✅ | ❌ |
| 选择文字 | ✅ | ✅ |
| 收藏 / 已收藏 | ✅ | ✅ |
| 删除 | ✅ | ✅ |
| 分享 | ✅ | ✅ |

分享通过 `Intent.ACTION_SEND` + `text/plain` + `Intent.createChooser` 调起系统分享面板，内容为消息正文纯文本（无角色前缀 / 时间戳）。

**视觉风格按 [design-system.md §6.7](./design-system.md#67-弹窗--菜单ios-popover-风格)（iOS Popover）；菜单左上角锚到按压点（Popup anchor 嵌入 0 大小 offset Box）**。

### 4.3 设置页收藏条目（参考微信收藏）

`SettingsScreen.FavoriteItem` 参考微信收藏的交互：**点击**整行打开对应会话并高亮定位（`viewModel.openFavorite`），**长按**在按压点弹出菜单（复制 / 删除收藏）。列表项**不再常驻删除按钮**，避免误触与视觉噪音。

菜单同样按 [design-system.md §6.7](./design-system.md#67-弹窗--菜单ios-popover-风格) 的 iOS Popover 风格，锚点方案与消息长按菜单一致（0 大小 `Modifier.offset` Box 内嵌 `DropdownMenu`）。

## 5. 路由与认证态的耦合

- `HomeScreen` 必须由 `canEnterHome` 的会话进入，否则会出现 UI 与认证态不一致；
- `LoginScreen` 顶栏「游客登录」按钮仅在 `!session.isGuest` 时显示（`showGuestLoginButton = !uiState.session.isGuest`）；
- 任何 `logout` 路径都会触发 `authRepository.session` 变化，根 ViewModel 接管路由，无需手动 `openLogin`。
