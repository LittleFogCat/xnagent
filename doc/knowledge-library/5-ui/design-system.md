# 设计系统

XNAgent 使用 Material3 作为设计语言基础，叠加一组业务色与少量自定义组件。本文档说明颜色、字体、间距、形状与常用组件规范。

## 1. 主题

- 主题入口：`ui/theme/Theme.kt#XNAgentTheme`；
- 颜色：`ui/theme/Color.kt` 提供命名色板令牌（`BrandBlue`、`BubbleUser`、`BubbleAssistant` 等），`LightColorScheme.primary = BrandBlue`；
- 形状：`ui/theme/Shape.kt` 定义 `XnShapes`（覆盖 Material3 默认 `Shapes`）和 `HeroShape`（24 dp），通过 `MaterialTheme(shapes = XnShapes, ...)` 注入；
- 字体：`ui/theme/Type.kt` 显式定义全部 15 个 Material3 Typography 槽位；
- 默认启用 **动态取色**（`dynamicColor = true`），Android 12+ 自动跟随系统；
- 关闭动态取色时使用 `Color.kt` 中的 `LightColorScheme`（`baseBg = #FFFFFFFF` 作为 background / surface）；
- 当前未自定义暗色调色板，依赖 `isSystemInDarkTheme()` + `darkColorScheme(...)` 默认值。

## 2. 颜色

### 2.1 业务色（`res/values/colors.xml`）

| 资源名 | 颜色 | 用途 |
| --- | --- | --- |
| `chat_bubble_bg_user` | `#E8F0FD` | 用户消息气泡背景（淡蓝） |
| `chat_bubble_bg_assist` | `#F4F4F4` | 助手消息气泡背景（中性灰） |
| `deepthink_text_color_checked` | `#3B75FA` | 深度思考按钮文本（开启） |
| `deepthink_text_color_unchecked` | `#000000` | 深度思考按钮文本（关闭） |
| `deepthink_bg_color_checked` | `#223B75FA` | 深度思考按钮背景（开启，13% 透明） |
| `deepthink_bg_color_unchecked` | `#00000000` | 深度思考按钮背景（关闭，透明） |

> 原 `chat_send_button_bg` / `deepthink_border_color_*` 已废弃：发送按钮现在通过 `MaterialTheme.colorScheme.primary`（= `BrandBlue` = `#3B75FA`，见 §2.3）取色，深度思考按钮边框用 Material3 OutlinedButton 默认描边，不再单独定义。

业务色通过 `colorResource(R.color.xxx)` 引用，禁止在 Composable 中写死十六进制（已存在的渐变背景色除外，见下文）。

### 2.2 登录页装饰色（`LoginScreen` 内联）

| 颜色 | 值 | 用途 |
| --- | --- | --- |
| 页面背景 | `#F4F8FB` | 整体底色 |
| 渐变顶栏 | `#E0F2FE → #F4F8FB` | 顶部 280dp 渐变 |
| 主文字 | `#0F172A` | 标题、正文 |
| 次文字 | `#475569` | 副标题、辅助信息 |
| 主色 | `#0EA5E9` | 输入聚焦、按钮、链接 |
| 主色软背景 | `#DFF7FF` | 状态提示容器 |
| 卡片边框 | `#E2E8F0` | 表单卡片描边 |
| 错误背景 | `#FFE5E5` | 错误提示容器 |
| 错误文字 | `#DC2626` | 错误提示文本 |

> 登录页装饰色为页面特定调色板，集中定义在 `LoginScreen.LoginContent` 顶部，便于整体替换。
> 其中 `accentColor` 已从原来的青色 `#0EA5E9` 统一为 `BrandBlue`（`ui/theme/Color.kt#BrandBlue`），与首页品牌色保持一致。

### 2.3 主题色（`ui/theme/Color.kt`）

- 浅色调色板：`primary = BrandBlue (#3B75FA)`、`secondary = PurpleGrey40`、`tertiary = Pink40`、`background = surface = baseBg (#FFFFFFFF)`；
- 深色调色板：`Purple80` / `PurpleGrey80` / `Pink80`；
- 业务色优先使用 `res/values/colors.xml`，主题色仅在 `XNAgentTheme` 中被消费。

### 2.4 色板令牌（`ui/theme/Color.kt`）

| 名称 | Hex | 用途 |
| --- | --- | --- |
| `BrandBlue` | `#3B75FA` | 主色：发送按钮、深度思考开启态、登录页强调色 |
| `BubbleUser` | `#E8F0FD` | 用户消息气泡 |
| `BubbleAssistant` | `#F4F4F4` | 助手消息气泡 |
| `TextPrimary` | `#0F172A` | 主文字 |
| `TextSecondary` | `#475569` | 次文字 |
| `SurfaceSubtle` | `#F4F8FB` | 登录页底色 |
| `OutlineBase` | `#E2E8F0` | 默认边框、未聚焦输入框描边 |
| `ErrorSurface` | `#FFE5E5` | 登录错误提示容器 |
| `OnError` | `#DC2626` | 登录错误提示文字 |
| `baseBg` | `#FFFFFFFF` | light theme background / surface fallback（dynamicColor 关闭时生效） |

> 业务色 / 品牌色调整都集中在 `Color.kt` 改名板令牌，调用方按名引用，避免散落十六进制。

## 3. 字体

- 字体方案：`ui/theme/Type.kt#Typography`（显式定义全部 15 个 Material3 Typography 槽位）；
- 字体族：`FontFamily.Default`，未引入自定义字体；
- 主动加粗的槽位：`headlineMedium` / `headlineSmall` / `titleLarge` / `titleMedium` 均为 SemiBold（`Type.kt` 内置，无需调用方 `.copy`）；
- 常用样式：

| 用途 | Style |
| --- | --- |
| 页面大标题 | `headlineMedium` / `headlineSmall` |
| 顶栏标题 | `titleMedium` |
| 卡片标题 | `titleLarge` / `titleMedium` |
| 正文 | `bodyMedium` / `bodyLarge` |
| 辅助说明 | `bodySmall` |
| 分组小标题 | `labelMedium` / `labelSmall` |
| 发送按钮文字 | `bodyLarge` + 12sp（如需） |

- 调用方不再使用 `MaterialTheme.typography.xxx.copy(fontWeight = …)` 加粗——加粗意图已收到 Typography 定义里；如需额外强调，使用更高一级的槽位（如 `bodyMedium` → `titleMedium`）。
- 两处保留的 per-call 加粗（这两处的诉求没法沉到 Typography 定义里，所以保留）：
  - `LoginScreen.kt:227` "XN" 品牌字：`headlineSmall.copy(fontWeight = Black, letterSpacing = 1.sp)`；
  - `LoginScreen.kt:647` 服务列表选中态：`style = bodyMedium` + `fontWeight = if (selected) SemiBold else Normal`（状态驱动的条件权重，没法写进槽位）。
- 字距：仅品牌头部「XN」使用 `letterSpacing = 1.sp`。

## 4. 间距

| 用途 | 间距 |
| --- | --- |
| 页面水平内边距 | `16.dp` / `20.dp` / `24.dp` |
| 区块上下间距 | `8.dp` / `12.dp` / `16.dp` |
| 卡片内边距 | `12.dp` / `20.dp` |
| 消息气泡之间 | `vertical = 4.dp` |
| 抽屉与右侧内容 | 由 `ModalNavigationDrawer` 自动控制 |
| 输入框与底部 | `12.dp, 6.dp` |

## 5. 形状

形状尺度集中在 `ui/theme/Shape.kt`：

```kotlin
val XnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
val HeroShape = RoundedCornerShape(24.dp)
```

通过 `MaterialTheme(shapes = XnShapes, ...)` 注入，调用方按 `MaterialTheme.shapes.xxx` 引用：

| Token | 半径 | 用途 |
| --- | --- | --- |
| `MaterialTheme.shapes.extraSmall` | 4 dp | 保留位（暂未使用） |
| `MaterialTheme.shapes.small` | 8 dp | 紧凑文本输入框（首页消息输入框） |
| `MaterialTheme.shapes.medium` | 12 dp | 抽屉选中项高亮 |
| `MaterialTheme.shapes.large` | 16 dp | iOS Popover 菜单容器（见 §6.7）、聊天气泡主圆角、信息提示条 |
| `MaterialTheme.shapes.extraLarge` | 20 dp | 卡片（设置 / 主输入条 / 登录表单控件 / 深度思考按钮 / 登录模式切换） |
| `HeroShape` | 24 dp | 视觉焦点容器：登录表单卡片、登录顶栏底角、`ChatInputBar` 输入框 |

**聊天气泡（特殊，非单一圆角）**：

| 角色 | 形状 |
| --- | --- |
| 用户 | `topStart = 16.dp, topEnd = 8.dp, bottomStart = 16.dp, bottomEnd = 16.dp` |
| 助手 | `topStart = 8.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp` |

> 8.dp 尾角比原来的 4.dp 更明显，但与 16.dp 主圆角的对比仍然存在。
>
> iOS Popover 菜单容器（见 §6.7）显式使用 `RoundedCornerShape(16.dp)` 字面量，**不**走 `MaterialTheme.shapes.large`，避免将来调整尺度时被波及——这是固定的 16.dp 视觉语言。

## 6. 组件规范

### 6.1 通用 Composable

| 组件 | 路径 | 职责 |
| --- | --- | --- |
| `ChatMessageList` / `ChatMessageItem` | `ui/component/ChatMessageList.kt` | 消息列表 + 消息项（长按菜单、Markdown、重新生成、收藏、复制、选择、删除） |
| `ChatInputBar` | `ui/component/ChatInputBar.kt` | 输入条（如已抽离） |
| `DropdownSelector` | `ui/component/DropdownSelector.kt` | 通用下拉选择器（首页模型选择） |
| `MarkdownText` | `ui/component/MarkdownText.kt` | 基于 Markwon 的 Markdown 渲染 |
| `UserAvatar` | `ui/component/UserAvatar.kt` | 头像（无图时显示昵称首字符） |
| `SettingsScreen` | `ui/screen/settings/SettingsScreen.kt` | 设置页（含 AlertDialog 二次确认） |

### 6.2 卡片 / 容器

- 使用 Material3 `Card` + `CardDefaults.cardElevation`；
- 主页输入区：`elevation = 4.dp`，`shape = MaterialTheme.shapes.extraLarge`（20 dp）；
- 登录页表单：`elevation = 10.dp`，`shape = HeroShape`（24 dp）；
- 完整形状尺度见 §5。

### 6.3 按钮

- 主操作：`Material3.Button`，统一 `shape = MaterialTheme.shapes.extraLarge`（20 dp）；
- 次操作：`OutlinedButton`，描边 + 透明背景，shape 同主操作；
- 图标按钮：`IconButton`，颜色优先从 `MaterialTheme.colorScheme` 取（动态主题 + 主题色都生效）；
- 深度思考开关：`OutlinedButton` + 状态色（见 §2.1 业务色）。

### 6.4 输入框

- 主页 `TextField`：`shape = MaterialTheme.shapes.small`（8 dp），透明背景 + 无 indicator，提示文案「输入消息…」；
- `ChatInputBar` `OutlinedTextField`：`shape = HeroShape`（24 dp）；
- 登录 `OutlinedTextField`：聚焦时描边切换为 `accentColor`（= `BrandBlue`，见 §2.4）；
- 所有输入框聚焦时通过 `bringIntoViewRequester` 滚动到可视区（登录页）。

### 6.5 列表 / 滚动

- 抽屉会话列表：`LazyColumn` + `key = "header_xxx"` / `"session_xxx"`；
- 消息列表：`LazyColumn` + `key = message.id`，自动滚动到底部由 Composable 内 `LaunchedEffect` 监听长度变化；
- 滚动条：长列表可考虑 `verticalScroll(rememberScrollState())`，但聊天列表统一使用 `LazyColumn`。

### 6.6 Loading Indicator（TypingIndicator）

用于 `ChatMessageList` 在助手正在生成回复、且气泡尚未出现时的占位提示。

| Token | 取值 | 用途 |
| --- | --- | --- |
| 点颜色（基础） | `MaterialTheme.colorScheme.onSurfaceVariant` | 三个圆点的基础颜色，与其它次级文字保持一致 |
| 点透明度（基础） | `0.45f` | 圆点静止时的透明度 |
| 点透明度（动画峰值） | `0.90f` | 圆点在动画峰值（位移 -4dp 顶部）时的透明度 |
| 点尺寸 | `6.dp` | 三个圆点的直径，比气泡字号略小，便于在等待时保持低调 |
| 点间距 | `4.dp` | 三个圆点之间的水平间距 |
| 容器水平 padding | `start = 16.dp / end = 16.dp` | 与 `ChatMessageItem` 助手气泡外层 padding 对齐，圆点紧贴气泡左边缘 |

对齐：

- `Row` 使用 `Arrangement.Start`，三个圆点靠左显示，与下方助手气泡的左侧边对齐；
- 顶部/底部 padding 仍为 `8.dp`，避免与上一条消息贴得太近。

动画：

- 三个圆点使用 `rememberInfiniteTransition` + `keyframes` 形成上下浮动 + 透明度变化的波浪；
- 持续时间 `1200ms`，三个点相位依次错开 `160ms`，**从左到右**依次点亮，模拟文字打出方向；
- 动画运行期不要在 `LazyColumn` 上重复创建此组件，应用 `item(key = "typing_indicator")` 锚定单实例。

可见性：

- `TypingIndicator` 只在 `isResponding && !hasLiveAssistant` 时显示，避免与正在增长的助手气泡同时出现造成视觉冗余；
- `hasLiveAssistant` 定义：消息列表中存在 `role == ASSISTANT && (isThinking || isGenerating)` 的项。

### 6.7 弹窗 / 菜单（iOS Popover 风格）

长按消息、长按会话条目等场景触发的 `DropdownMenu` 统一采用 iOS 长按弹窗风格，保证视觉一致性。

**容器参数**：

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| `shape` | `RoundedCornerShape(16.dp)` | 大圆角，弱化「Material 列表」感 |
| `shadowElevation` | `8.dp` | 轻阴影，避免过重投影 |
| `containerColor` | `Color.White.copy(alpha = 0.96f)` | 微透明白底，与气泡背景区分 |
| `border` | `BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.06f))` | 极细灰边，辅助分层 |

**菜单项（`DropdownMenuItem`）**：

| 参数 | 取值 |
| --- | --- |
| `contentPadding` | `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` |
| `leadingIcon` | **必填**：所有菜单项必须带图标，统一「图标 + 文字」格式 |

**项间分隔线**：使用公共组件 `IosMenuDivider`，参数：

- `thickness = 0.5.dp`
- `color = Color.Black.copy(alpha = 0.08f)`

`IosMenuDivider` 已抽到 `app/src/main/java/tech/xiaoniu/xnagent/ui/component/IosMenuDivider.kt`，所有 iOS Popover 风格菜单统一引用。

**适用范围**：本规范适用于所有浮动弹出的菜单 / 选择器，包括但不限于：

- `DropdownMenu`（长按消息、长按会话条目等触发）
- `ExposedDropdownMenu`（首页「模型选择器」 `DropdownSelector.kt`）

模态对话框 `AlertDialog` 跟浮动菜单视觉场景不同，**不适用本规范**，沿用 Material3 默认即可。

**锚点策略**（关键技术点，违反会导致菜单跑偏）：

- Material3 `DropdownMenu` 内部用 `Popup` 实现。`Popup` 的 anchor 来自它在 Compose 树中**直接的 Layout 父节点**（参见 `AndroidPopup.android.kt` 中 `childCoordinates.parentLayoutCoordinates` 的用法）。
- 必须把 `DropdownMenu` 嵌入到「按压点位置的 0 大小 Box」**内部**作为子节点：
  ```kotlin
  Box(
      modifier = Modifier
          .offset(actionMenuOffset.x, actionMenuOffset.y)
          .size(0.dp)
  ) {
      DropdownMenu(
          expanded = ...,
          offset = DpOffset.Zero,  // 关键：传 Zero，offset 已在 Box 上
      ) { ... }
  }
  ```
- 这样 `anchorBounds = (按压点, 0, 0)`，`DropdownMenu` 默认「菜单左上对齐 anchor 左上」即可精准锚到按压点。
- `actionMenuOffset` 通过 `Modifier.pointerInput(key) { detectTapGestures(onLongPress = { offset -> ... }) }` 捕获的 `Offset` 转 `DpOffset` 得到。
- ❌ 不能用 `combinedClickable` 的 `onLongClick`：本项目 Compose 版本没有接收 `Offset` 的 `combinedClickable` 重载，无法捕获按压点位置。

**为什么不用 `DropdownMenu` 自带的 `offset` 参数传按压点**：

`DropdownMenu` 的 `offset` 是基于 Popup **自动选定的对齐边**（X 默认对齐 anchor 左边，Y 默认对齐 anchor 底部）再加偏移，不是从 anchor 左上角开始算。直接传按压点坐标会让菜单跑到气泡 / 行边界外。

## 7. 动效

- 消息项中思考过程展开：`expandVertically` / `shrinkVertically`，时长 220ms；
- 设置页行展开 / 收起：`AnimatedVisibility` + `expandVertically` / `shrinkVertically`，时长 220ms（`SECTION_ANIM_DURATION_MS`）；
- 设置页行右侧箭头旋转：`animateFloatAsState` + `Modifier.rotate`，与展开动画同步（220ms），收起时箭头朝右，展开后旋转 90° 朝下；
- 无子列表的设置项不渲染右侧箭头（避免点击无反馈）；
- 渐变背景：登录页静态渐变，不使用动画；
- 加载占位：消息生成中显示「思考中…」占位文案 + 折叠态 reasoning。

## 8. 国际化

- 当前统一中文写死到 Composable 字符串；
- 未来需要 i18n 时，将中文文案迁移到 `res/values/strings.xml` 与 `res/values-en/strings.xml`；
- 业务色 / 主题色与语言无关，无需迁移。

## 9. 资源命名

详见 [`../2-convention/naming.md`](../2-convention/naming.md) 第 4 节「资源」。
