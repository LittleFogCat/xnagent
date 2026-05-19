package tech.xiaoniu.xnagent.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 简单的用户头像占位。
 */
@Composable
fun UserAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val initials = label
        .trim()
        .split(" ", "@")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString(separator = "") { it.take(1) }
        .ifBlank { "XN" }
        .uppercase()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF101827),
                        Color(0xFF00C2FF),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}