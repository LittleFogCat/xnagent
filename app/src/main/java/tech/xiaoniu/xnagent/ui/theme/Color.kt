package tech.xiaoniu.xnagent.ui.theme

import androidx.compose.ui.graphics.Color

// 业务色板令牌。详见 doc/knowledge-library/5-ui/design-system.md §2.4。
// 任何对品牌色 / 业务色的调整都集中在这里修改。

/** 品牌主色：发送按钮、深度思考开启态、登录页强调色。 */
val BrandBlue = Color(0xFF3B75FA)

/** 用户消息气泡背景（淡蓝）。 */
val BubbleUser = Color(0xFFE8F0FD)

/** 助手消息气泡背景（中性灰）。 */
val BubbleAssistant = Color(0xFFF4F4F4)

/** 主文字（高强调度）。 */
val TextPrimary = Color(0xFF0F172A)

/** 次文字（中强调度）。 */
val TextSecondary = Color(0xFF475569)

/** 登录页底色。 */
val SurfaceSubtle = Color(0xFFF4F8FB)

/** 默认边框、未聚焦输入框描边。 */
val OutlineBase = Color(0xFFE2E8F0)

/** 登录错误提示容器背景。 */
val ErrorSurface = Color(0xFFFFE5E5)

/** 登录错误提示文字。 */
val OnError = Color(0xFFDC2626)

/**
 * 浅色主题 background / surface 默认值。
 *
 * 仅在 dynamicColor 关闭或 SDK < 31 时生效；dynamicColor=true 时由系统取色覆盖。
 * 修复之前 `baseBg = Color(0xFF000000)`（黑底）在浅色主题下的明显 bug。
 */
val baseBg = Color(0xFFFFFFFF)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)