package tech.xiaoniu.xnagent.ui.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * iOS 弹窗风格菜单项之间的细分割线。
 *
 * 详见 doc/knowledge-library/5-ui/design-system.md §6.7。0.5dp + alpha 0.08f 黑色，
 * 比 Material3 默认的 HorizontalDivider 颜色更淡，符合 iOS 长按弹窗的视觉密度。
 */
@Composable
fun IosMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color.Black.copy(alpha = 0.08f),
    )
}