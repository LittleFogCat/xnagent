package tech.xiaoniu.xnagent.ui.component

import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/**
 * 使用 Markwon 渲染 Markdown 文本。
 *
 * 关键点：通过匿名 TextView 子类 override [TextView.dispatchTouchEvent] 一律返回 false，
 * 让 TextView 不参与触摸分发，所有 DOWN / MOVE / UP 全部穿透到外层 Compose Box 的
 * detectTapGestures。这是唯一可靠的修复方式——之前的 isLongClickable / setMovementMethod
 * / setLinksClickable 都只是配合字段，决定不了 TextView 在 ACTION_DOWN 时返回 true 的行为。
 *
 * 链接点击能力作为 trade-off 暂时牺牲（dispatchTouchEvent 不再调用 TextView 的 onTouchEvent，
 * LinkMovementMethod 也就没机会处理链接）。未来如果要恢复链接点击，需要在 override 里
 * 手动根据 Spannable 中的 URLSpan 位置判断是否消费 ACTION_UP。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    selectable: Boolean = false,
) {
    val context = LocalContext.current
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val markwon = remember(context) {
        Markwon.create(context)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            object : TextView(viewContext) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    // 关键：永远返回 false，事件直接穿透到父 Compose 节点。
                    return false
                }
            }.apply {
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { textView ->
            textView.setTextIsSelectable(selectable)
            textView.setTextColor(textColor.toArgb())
            if (bodyLarge.fontSize.value > 0f) {
                textView.textSize = bodyLarge.fontSize.value
            }
            markwon.setMarkdown(textView, text)
            // 防御性关闭：dispatchTouchEvent 已经 return false，下面这些字段其实不影响分发结果，
            // 但保留以防未来 AndroidView 内部用其他方式判断消费。
            textView.movementMethod = null
            textView.linksClickable = false
            textView.isClickable = false
            textView.isFocusable = false
            textView.isFocusableInTouchMode = false
            textView.isLongClickable = selectable
            textView.setOnLongClickListener(null)
            textView.setOnClickListener(null)
        }
    )
}