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
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
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
                setTextIsSelectable(false)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                includeFontPadding = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor.toArgb())
            if (bodyLarge.fontSize.value > 0f) {
                textView.textSize = bodyLarge.fontSize.value
            }
            markwon.setMarkdown(textView, text)
        }
    )
}