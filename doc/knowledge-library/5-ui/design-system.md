# 设计系统

XNAgent 使用 Material3 作为设计语言基础，叠加一组业务色与少量自定义组件。本文档说明颜色、字体、间距、形状与常用组件规范。

## 1. 主题

- 主题入口：`ui/theme/Theme.kt#XNAgentTheme`；
- 默认启用 **动态取色**（`dynamicColor = true`），Android 12+ 自动跟随系统；
- 关闭动态取色时使用 `Color.kt` 中的 `LightColorScheme`（`baseBg` 作为 background / surface）；
- 当前未自定义暗色调色板，依赖 `isSystemInDarkTheme()` + `darkColorScheme(...)` 默认值。

## 2. 颜色

### 2.1 业务色（`res/values/colors.xml`）

| 资源名 | 颜色 | 用途 |
| --- | --- | --- |
| `chat_bubble_bg_user` | `#E8F0FD` | 用户消息气泡背景（淡蓝） |
| `chat_bubble_bg_assist` | `#F4F4F4` | 助手消息气泡背景（中性灰） |
| `chat_send_button_bg` | `#3B75FA` | 发送按钮图标 / 主操作色 |
| `deepthink_text_color_checked` | `#3B75FA` | 深度思考按钮文本（开启） |
| `deepthink_text_color_unchecked` | `#000000` | 深度思考按钮文本（关闭） |
| `deepthink_border_color_checked` | `#553B75FA` | 深度思考按钮边框（开启，50% 透明） |
| `deepthink_border_color_unchecked` | `#55000000` | 深度思考按钮边框（关闭，50% 透明） |
| `deepthink_bg_color_checked` | `#223B75FA` | 深度思考按钮背景（开启，13% 透明） |
| `deepthink_bg_color_unchecked` | `#00000000` | 深度思考按钮背景（关闭，透明） |
| `white` / `black` | 系统色 | 通用白底 / 黑底 |

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

### 2.3 主题色（`ui/theme/Color.kt`）

- 浅色调色板：`Purple40` / `PurpleGrey40` / `Pink40` + `baseBg` 作为 background / surface；
- 深色调色板：`Purple80` / `PurpleGrey80` / `Pink80`；
- 业务色优先使用 `res/values/colors.xml`，主题色仅在 `XNAgentTheme` 中被消费。

## 3. 字体

- 字体方案：`ui/theme/Type.kt#Typography`（沿用 Material3 默认 Typography）；
- 常用样式：

| 用途 | Style |
| --- | --- |
| 页面大标题 | `headlineMedium` / `headlineSmall` |
| 卡片标题 | `titleMedium` |
| 正文 | `bodyMedium` / `bodyLarge` |
| 辅助说明 | `bodySmall` |
| 分组小标题 | `labelMedium` / `labelSmall` |
| 发送按钮文字 | `bodyLarge` + 12sp（如需） |

- 加粗：`MaterialTheme.typography.xxx.copy(fontWeight = FontWeight.Bold / SemiBold / Black)`；
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

| 用途 | 形状 |
| --- | --- |
| 消息气泡（用户） | `RoundedCornerShape(topStart = 16, topEnd = 4, bottomStart = 16, bottomEnd = 16)` |
| 消息气泡（助手） | `RoundedCornerShape(topStart = 4, topEnd = 16, bottomStart = 16, bottomEnd = 16)` |
| 卡片 / 输入容器 | `RoundedCornerShape(20.dp)` |
| 登录表单卡片 | `RoundedCornerShape(30.dp)` |
| 输入框 | `RoundedCornerShape(8.dp)` / `RoundedCornerShape(18.dp)` |
| 模式切换容器 | `RoundedCornerShape(18.dp)` |
| 头像 / 圆形装饰 | `CircleShape` |

## 6. 组件规范

### 6.1 通用 Composable

| 组件 | 路径 | 职责 |
| --- | --- | --- |
| `ChatMessageList` / `ChatMessageItem` | `ui/component/ChatMessageList.kt` | 消息列表 + 消息项（长按菜单、Markdown、重新生成、收藏、复制、选择、删除） |
| `ChatInputBar` | `ui/component/ChatInputBar.kt` | 输入条（如已抽离） |
| `DropdownSelector` | `ui/component/DropdownSelector.kt` | 通用下拉选择器（首页模型选择） |
| `MarkdownText` | `ui/component/MarkdownText.kt` | 基于 Markwon 的 Markdown 渲染 |
| `UserAvatar` | `ui/component/UserAvatar.kt` | 头像（无图时显示昵称首字符） |

### 6.2 卡片 / 容器

- 使用 Material3 `Card` + `CardDefaults.cardElevation`；
- 主页输入区：`elevation = 4.dp`，`shape = RoundedCornerShape(20.dp)`；
- 登录页表单：`elevation = 10.dp`，`shape = RoundedCornerShape(30.dp)`。

### 6.3 按钮

- 主操作：`Material3.Button`，统一 `shape = RoundedCornerShape(18.dp)`；
- 次操作：`OutlinedButton`，描边 + 透明背景；
- 图标按钮：`IconButton`，优先使用 `colorResource` 设定色值；
- 深度思考开关：`OutlinedButton` + 状态色（见 §2.1 业务色）。

### 6.4 输入框

- 主页 `TextField`：透明背景 + 无 indicator，提示文案「输入消息…」；
- 登录 `OutlinedTextField`：聚焦时描边切换为 `accentColor`（`#0EA5E9`）；
- 所有输入框聚焦时通过 `bringIntoViewRequester` 滚动到可视区（登录页）。

### 6.5 列表 / 滚动

- 抽屉会话列表：`LazyColumn` + `key = "header_xxx"` / `"session_xxx"`；
- 消息列表：`LazyColumn` + `key = message.id`，自动滚动到底部由 Composable 内 `LaunchedEffect` 监听长度变化；
- 滚动条：长列表可考虑 `verticalScroll(rememberScrollState())`，但聊天列表统一使用 `LazyColumn`。

## 7. 动效

- 消息项中思考过程展开：`expandVertically` / `shrinkVertically`，时长 220ms；
- 渐变背景：登录页静态渐变，不使用动画；
- 加载占位：消息生成中显示「思考中…」占位文案 + 折叠态 reasoning。

## 8. 国际化

- 当前统一中文写死到 Composable 字符串；
- 未来需要 i18n 时，将中文文案迁移到 `res/values/strings.xml` 与 `res/values-en/strings.xml`；
- 业务色 / 主题色与语言无关，无需迁移。

## 9. 资源命名

详见 [`../2-convention/naming.md`](../2-convention/naming.md) 第 4 节「资源」。
