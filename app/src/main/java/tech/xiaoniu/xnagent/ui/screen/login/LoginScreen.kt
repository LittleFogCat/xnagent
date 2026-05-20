package tech.xiaoniu.xnagent.ui.screen.login

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlin.random.Random
import tech.xiaoniu.xnagent.ui.model.LoginUiState

/**
 * 登录/注册页面。
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
    showGuestLoginButton: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    LoginContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::dispatch,
        showGuestLoginButton = showGuestLoginButton,
        onBack = onBack,
    )
}

@Composable
fun LoginContent(
    modifier: Modifier = Modifier,
    uiState: LoginUiState,
    onAction: (LoginIntent) -> Unit = {},
    showGuestLoginButton: Boolean = true,
    onBack: (() -> Unit)? = null,
) {
    val backgroundColor = Color(0xFFF4F8FB)
    val primaryText = Color(0xFF0F172A)
    val secondaryText = Color(0xFF475569)
    val accentColor = Color(0xFF0EA5E9)
    val accentSoft = Color(0xFFDFF7FF)
    val cardBorder = Color(0xFFE2E8F0)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor = Color.White,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = cardBorder,
        focusedTextColor = primaryText,
        unfocusedTextColor = primaryText,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = secondaryText,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE0F2FE), backgroundColor),
                    ),
                    shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp),
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 48.dp)
                .size(148.dp)
                .clip(CircleShape)
                .background(Color(0x5538BDF8))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = 156.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0x40FFFFFF))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.Transparent, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = primaryText,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (showGuestLoginButton) {
                    OutlinedButton(
                        onClick = { onAction(LoginIntent.ContinueAsGuest) },
                        enabled = !uiState.isSubmitting,
                        border = BorderStroke(1.dp, Color(0xFFA5D8F3)),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(text = "游客登录")
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = Color.White/*.copy(alpha = 0.8f)*/,
                    shape = CircleShape,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(74.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "XN",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = primaryText,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                Text(
                    text = if (uiState.isRegisterMode) "注册 XN Agent" else "登录 XN Agent",
                    style = MaterialTheme.typography.headlineMedium,
                    color = primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uiState.isRegisterMode) {
                        "现代极简的对话入口，完成邮箱验证后即可开始完整体验"
                    } else {
                        "与聊天页一致的简洁交互，登录后继续你的对话和收藏"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryText,
                )
            }

            Card(
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                border = BorderStroke(1.dp, cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(18.dp))
                            .padding(4.dp),
                    ) {
                        LoginModeTab(
                            title = "登录",
                            selected = !uiState.isRegisterMode,
                            onClick = {
                                if (uiState.isRegisterMode) {
                                    onAction(LoginIntent.ToggleMode)
                                }
                            },
                        )
                        LoginModeTab(
                            title = "注册",
                            selected = uiState.isRegisterMode,
                            onClick = {
                                if (!uiState.isRegisterMode) {
                                    onAction(LoginIntent.ToggleMode)
                                }
                            },
                        )
                    }

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { onAction(LoginIntent.UpdateEmail(it)) },
                        label = { Text("邮箱") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        isError = uiState.emailError != null,
                        supportingText = {
                            uiState.emailError?.let { Text(text = it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { onAction(LoginIntent.UpdatePassword(it)) },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = uiState.passwordError != null,
                        supportingText = {
                            uiState.passwordError?.let { Text(text = it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )

                    if (!uiState.isRegisterMode && uiState.loginCaptchaRequired) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = accentSoft,
                        ) {
                            CaptchaQuestionImage(
                                question = uiState.loginCaptchaValue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                            )
                        }

                        OutlinedTextField(
                            value = uiState.loginCaptchaAnswer,
                            onValueChange = { onAction(LoginIntent.UpdateCaptchaAnswer(it)) },
                            label = { Text("图形验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            isError = uiState.loginCaptchaAnswerError != null,
                            supportingText = {
                                uiState.loginCaptchaAnswerError?.let { Text(text = it) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewOnFocus(),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                        )

                        TextButton(
                            onClick = { onAction(LoginIntent.RefreshCaptcha) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("刷新验证码")
                        }
                    }

                    if (uiState.isRegisterMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = accentSoft,
                        ) {
                            if (uiState.captchaQuestion.isBlank()) {
                                Text(
                                    text = "正在加载人机验证...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = primaryText,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            } else {
                                CaptchaQuestionImage(
                                    question = uiState.captchaQuestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.captchaAnswer,
                            onValueChange = { onAction(LoginIntent.UpdateCaptchaAnswer(it)) },
                            label = { Text("人机验证答案") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = uiState.captchaAnswerError != null,
                            supportingText = {
                                uiState.captchaAnswerError?.let { Text(text = it) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewOnFocus(),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                        )

                        Button(
                            onClick = { onAction(LoginIntent.RequestRegisterCode) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(text = if (uiState.codeRequested) "重新发送验证码" else "发送验证码")
                        }

                        TextButton(
                            onClick = { onAction(LoginIntent.RefreshCaptcha) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("刷新题目")
                        }

                        if (uiState.codeRequested) {
                            OutlinedTextField(
                                value = uiState.verificationCode,
                                onValueChange = { onAction(LoginIntent.UpdateVerificationCode(it)) },
                                label = { Text("邮箱验证码") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = uiState.verificationCodeError != null,
                                supportingText = {
                                    uiState.verificationCodeError?.let { Text(text = it) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewOnFocus(),
                                shape = RoundedCornerShape(18.dp),
                                colors = fieldColors,
                            )

                            Button(
                                onClick = { onAction(LoginIntent.CompleteRegister) },
                                enabled = !uiState.isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Text("完成注册")
                            }
                        }
                    } else {
                        Button(
                            onClick = { onAction(LoginIntent.Login) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("登录")
                        }
                    }

                    uiState.noticeMessage?.let {
                        StatusMessage(
                            text = it,
                            containerColor = accentSoft,
                            textColor = accentColor,
                        )
                    }

                    uiState.errorMessage?.let {
                        StatusMessage(
                            text = it,
                            containerColor = Color(0xFFFFE5E5),
                            textColor = Color(0xFFDC2626),
                        )
                    }

                    Text(
                        text = if (uiState.isRegisterMode) {
                            "完成注册后将自动进入主会话列表。"
                        } else {
                            "登录后可同步聊天记录、收藏和智能体会话。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    requester.bringIntoView()
                }
            }
        }
}

@Composable
private fun CaptchaQuestionImage(
    question: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { 320.dp.roundToPx() }
    val heightPx = with(density) { 96.dp.roundToPx() }
    val captchaBitmap = remember(question, widthPx, heightPx) {
        createCaptchaBitmap(
            question = question,
            widthPx = widthPx,
            heightPx = heightPx,
        ).asImageBitmap()
    }

    Image(
        bitmap = captchaBitmap,
        contentDescription = "人机验证题目",
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

private fun createCaptchaBitmap(
    question: String,
    widthPx: Int,
    heightPx: Int,
): Bitmap {
    val safeQuestion = question.ifBlank { "..." }
    val random = Random(safeQuestion.hashCode())
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val palette = intArrayOf(
        AndroidColor.parseColor("#0F172A"),
        AndroidColor.parseColor("#0EA5E9"),
        AndroidColor.parseColor("#0284C7"),
        AndroidColor.parseColor("#1E293B"),
    )

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            AndroidColor.parseColor("#F8FBFF"),
            AndroidColor.parseColor("#DFF7FF"),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), backgroundPaint)

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = heightPx * 0.02f
    }
    repeat(8) {
        linePaint.color = palette[random.nextInt(palette.size)]
        linePaint.alpha = random.nextInt(70, 140)
        val path = Path().apply {
            moveTo(random.nextFloat() * widthPx, random.nextFloat() * heightPx)
            quadTo(
                random.nextFloat() * widthPx,
                random.nextFloat() * heightPx,
                random.nextFloat() * widthPx,
                random.nextFloat() * heightPx,
            )
        }
        canvas.drawPath(path, linePaint)
    }

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(90) {
        dotPaint.color = palette[random.nextInt(palette.size)]
        dotPaint.alpha = random.nextInt(25, 80)
        canvas.drawCircle(
            random.nextFloat() * widthPx,
            random.nextFloat() * heightPx,
            random.nextFloat() * heightPx * 0.05f + 1f,
            dotPaint,
        )
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = heightPx * 0.38f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val slotWidth = widthPx.toFloat() / (safeQuestion.length + 1)
    val baseline = heightPx * 0.62f
    safeQuestion.forEachIndexed { index, char ->
        val drawX = slotWidth * (index + 1) + (random.nextFloat() - 0.5f) * slotWidth * 0.24f
        val drawY = baseline + (random.nextFloat() - 0.5f) * heightPx * 0.16f
        textPaint.color = palette[random.nextInt(palette.size)]

        canvas.save()
        canvas.rotate((random.nextFloat() - 0.5f) * 36f, drawX, drawY)
        canvas.scale(
            1f + (random.nextFloat() - 0.5f) * 0.18f,
            1f + (random.nextFloat() - 0.5f) * 0.12f,
            drawX,
            drawY,
        )
        canvas.drawText(char.toString(), drawX, drawY, textPaint)
        canvas.restore()
    }

    return bitmap
}

@Composable
private fun RowScope.LoginModeTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Color(0xFFD7E3F4) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color(0xFF0F172A) else Color(0xFF64748B),
        )
    }
}

@Composable
private fun StatusMessage(
    text: String,
    containerColor: Color,
    textColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Preview
@Composable
private fun LoginPreview() {
    LoginContent(
        uiState = LoginUiState(

        ),
        showGuestLoginButton = true,
        onBack = {},
    )
}