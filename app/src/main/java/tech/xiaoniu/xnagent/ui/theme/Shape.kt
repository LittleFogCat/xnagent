package tech.xiaoniu.xnagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用形状尺度。
 *
 * 详见 [doc/knowledge-library/5-ui/design-system.md §5](../../../../../doc/knowledge-library/5-ui/design-system.md)。
 * 通过 `MaterialTheme(shapes = XnShapes, ...)` 注入主题，覆盖 Material3 默认值。
 *
 * | Token        | Radius | 典型场景 |
 * | ---          | ---    | ---      |
 * | extraSmall   | 4 dp   | 保留位（暂未使用） |
 * | small        | 8 dp   | 紧凑文本输入框 |
 * | medium       | 12 dp  | 抽屉选中项 |
 * | large        | 16 dp  | iOS Popover 菜单、聊天气泡主圆角 |
 * | extraLarge   | 20 dp  | 卡片、主输入条、深度思考按钮、设置卡片、表单控件 |
 */
val XnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Hero 形状：24 dp，超出 `XnShapes` 尺度但仍是 Material3 推荐上限。
 * 用于视觉焦点容器（登录表单卡片、登录顶栏底角、聊天输入框）。
 */
val HeroShape = RoundedCornerShape(24.dp)