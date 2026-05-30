package tech.xiaoniu.xnagent.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight()
                    .weight(1f)
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
        ) {
            items.forEach { item ->
                // 选中某项后立即回调并关闭下拉菜单。
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
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
