package tech.xiaoniu.xnagent.ui.component

import android.text.method.LinkMovementMethod
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
 * 这里通过 AndroidView 承接原生 TextView，便于直接复用 Markwon 的链接和富文本能力。
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
            TextView(viewContext).apply {
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { textView ->
            // 选择文字与点击链接是互斥的：进入选择模式时关闭链接点击能力。
            textView.setTextIsSelectable(selectable)
            textView.movementMethod = if (selectable) null else LinkMovementMethod.getInstance()
            textView.linksClickable = !selectable
            textView.setTextColor(textColor.toArgb())
            if (bodyLarge.fontSize.value > 0f) {
                textView.textSize = bodyLarge.fontSize.value
            }
            markwon.setMarkdown(textView, text)
        }
    )
}