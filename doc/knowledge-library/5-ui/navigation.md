# 导航

XNAgent 使用 **唯一 Activity + 状态驱动路由**，未引入 Jetpack Navigation 库。所有路由由 `MainViewModel.uiState.destination` 决定。

## 1. 顶级 Destination

`MainDestination`（`MainViewModel.kt`）：

```kotlin
sealed interface MainDestination {
    data object Home : MainDestination
    data object Settings : MainDestination
    data object Login : MainDestination
}
```

`MainActivity` 中：

```kotlin
when (uiState.destination) {
    MainDestination.Home     -> HomeScreen(...)
    MainDestination.Settings -> SettingsScreen(...)
    MainDestination.Login    -> LoginScreen(...)
}
```

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
        │                                          │       │
        │   返回 / 退出登录 / 清除本地数据          │       │
        └──────────────────────────────────────────┘       │
```

`MainViewModel` 行为：

- 初始：根据 `authRepository.session.value.canEnterHome` 决定 `Home` 或 `Login`；
- 订阅 `authRepository.session`：
  - `!canEnterHome` → `Login`；
  - 当前为 `Login` 且 `canEnterHome` → `Home`；
  - 其余情况保留 `destination`（保留从 Home 进入 Settings 的状态）；
- `openHome` / `openSettings` / `openLogin` / `logout` 提供显式切换。

## 3. 顶级页面之间的跳转

| 触发 | 来源 | 行为 |
| --- | --- | --- |
| 启动 | `MainActivity.onCreate` | 根据认证态路由 |
| 抽屉 → 设置 | `HomeScreen.DrawerContent.onOpenSettings` | `viewModel::openSettings` |
| 抽屉 → 登录 | `HomeScreen.DrawerContent.onOpenLogin` | `viewModel::openLogin` |
| 抽屉 → 新对话 | `HomeScreen.DrawerContent.onNewChat` | `HomeIntent.CreateNewChat`（仍在 Home） |
| 游客达到消息上限 | `HomeScreen` 顶部提示 | `onOpenLogin` → `openLogin` |
| 设置 → 返回 | `SettingsScreen` 顶栏返回 | `onBack = viewModel::openHome` |
| 设置 → 退出 / 登录 | `SettingsScreen` 底部 | 已登录：调用 `viewModel.logout()`；游客：调用 `viewModel.openLogin()` |
| 登录 → 返回 | `LoginScreen`（已登录状态下显示） | `onBack = viewModel::openHome` |
| 登录 → 游客 | `LoginScreen` 顶栏 | `LoginIntent.ContinueAsGuest` |

## 4. 内部导航

### 4.1 首页抽屉（ModalNavigationDrawer）

`HomeScreen.HomeScreenContent` 使用 `ModalNavigationDrawer`，抽屉内容 `DrawerContent` 自上而下：

1. 标题「聊天记录」+「新对话」按钮；
2. 历史会话列表（按 `groupByDate()` 分组：今天 / 昨天 / 本周 / 更早）；
3. 底部用户信息区：
   - 已登录：显示昵称、邮箱、「设置」按钮；
   - 游客：显示「游客」+「前往登录」按钮。

### 4.2 消息长按菜单

`ChatMessageList.ChatMessageItem` 长按触发菜单：

| 选项 | 用户消息 | 助手消息 |
| --- | --- | --- |
| 复制 | ❌ | ✅ |
| 收藏 / 取消收藏 | ✅ | ✅ |
| 重新生成 | ❌ | ✅ |
| 编辑（仅 USER） | ✅ | ❌ |
| 选择文字 | ✅ | ✅ |
| 删除 | ✅ | ✅ |

## 5. 路由与认证态的耦合

- `HomeScreen` 必须由 `canEnterHome` 的会话进入，否则会出现 UI 与认证态不一致；
- `LoginScreen` 顶栏「游客登录」按钮仅在 `!session.isGuest` 时显示（`showGuestLoginButton = !uiState.session.isGuest`）；
- 任何 `logout` 路径都会触发 `authRepository.session` 变化，根 ViewModel 接管路由，无需手动 `openLogin`。
