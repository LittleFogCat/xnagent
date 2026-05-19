package tech.xiaoniu.xnagent.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import tech.xiaoniu.xnagent.ui.model.LoginUiState

/**
 * 登录/注册页面。
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LoginContent(
        modifier = modifier,
        uiState = uiState,
        onAction = viewModel::dispatch,
    )
}

@Composable
fun LoginContent(
    modifier: Modifier = Modifier,
    uiState: LoginUiState,
    onAction: (LoginIntent) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TextButton(
            onClick = { onAction(LoginIntent.ContinueAsGuest) },
            enabled = !uiState.isSubmitting,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(text = "游客登录")
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (uiState.isRegisterMode) "注册 XN Agent" else "登录 XN Agent",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = if (uiState.isRegisterMode) {
                        "完成邮箱验证后将自动登录"
                    } else {
                        "使用邮箱账号登录"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = { onAction(LoginIntent.UpdateEmail(it)) },
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { onAction(LoginIntent.UpdatePassword(it)) },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.isRegisterMode) {
                    Text(
                        text = if (uiState.captchaQuestion.isBlank()) "正在加载人机验证..." else uiState.captchaQuestion,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    OutlinedTextField(
                        value = uiState.captchaAnswer,
                        onValueChange = { onAction(LoginIntent.UpdateCaptchaAnswer(it)) },
                        label = { Text("人机验证答案") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = { onAction(LoginIntent.RequestRegisterCode) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            onClick = { onAction(LoginIntent.CompleteRegister) },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("完成注册")
                        }
                    }
                } else {
                    Button(
                        onClick = { onAction(LoginIntent.Login) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("登录")
                    }
                }

                uiState.noticeMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                TextButton(
                    onClick = { onAction(LoginIntent.ToggleMode) },
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = if (uiState.isRegisterMode) {
                            "已有账号？去登录"
                        } else {
                            "没有账号？去注册"
                        }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginPreview() {
    LoginContent(
        uiState = LoginUiState(

        ),
    )
}