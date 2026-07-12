package tech.xiaoniu.xnagent.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 通用下拉选择器。
 *
 * 当前主要用于模型切换，但组件本身与具体业务类型无关。
 * 弹出菜单按设计系统 §6.7 落地 iOS Popover 风格（圆角 / 阴影 / 边框 / 项间分隔线）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    modifier: Modifier = Modifier,
    items: List<T>,
    itemToText: T.() -> String,
    onItemSelect: (T) -> Unit,
    border: Boolean = true,
    contentPadding: PaddingValues = ExposedDropdownMenuDefaults.ItemContentPadding,
    child: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxSize(),
    ) {
        // 锚点区域由外部 child 决定当前展示内容，右侧统一复用 Material3 展开箭头。
        // Box 默认 wrap content 宽度 + spacedBy(4.dp) 让文字与下拉箭头紧贴 4dp，避免 child 用 fillMaxWidth
        // 把 Box 撑满整个 Row，导致选择器看起来「文字部分过宽」。
        Row(
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxSize()
                .background(Color.White)
                .padding(contentPadding)
                .apply {
                    if (border) {
                        border(1.dp, MaterialTheme.colorScheme.outline, shape = OutlinedTextFieldDefaults.shape)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.wrapContentHeight()
            ) {
                child()
            }
            ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = expanded,
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 220.dp)
                .background(Color.White),
            matchAnchorWidth = false,
            // iOS Popover 风格：与长按菜单保持一致的视觉语言。
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            containerColor = Color.White.copy(alpha = 0.96f),
            border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.06f)),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    IosMenuDivider()
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.itemToText(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onItemSelect(item)
                        expanded = false
                    },
                    // 与长按菜单保持一致的紧凑内边距。
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
