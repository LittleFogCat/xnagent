package tech.xiaoniu.xnagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import tech.xiaoniu.xnagent.ui.screen.home.HomeScreen
import tech.xiaoniu.xnagent.ui.screen.login.LoginScreen
import tech.xiaoniu.xnagent.ui.screen.settings.SettingsScreen
import tech.xiaoniu.xnagent.ui.theme.XNAgentTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XNAgentTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (uiState.destination) {
                        MainDestination.Home -> {
                            HomeScreen(
                                onOpenSettings = viewModel::openSettings,
                                onOpenLogin = viewModel::openLogin,
                            )
                        }

                        MainDestination.Settings -> {
                            SettingsScreen(
                                isGuest = uiState.session.isGuest,
                                displayName = uiState.session.user?.username?.substringBefore('@')
                                    ?: "游客模式",
                                email = uiState.session.user?.email.orEmpty(),
                                onBack = viewModel::openHome,
                                onBackToLogin = if (uiState.session.isGuest) {
                                    viewModel::openLogin
                                } else {
                                    viewModel::logout
                                },
                            )
                        }

                        MainDestination.Login -> {
                            LoginScreen(
                                showGuestLoginButton = !uiState.session.isGuest,
                                onBack = if (uiState.session.canEnterHome) {
                                    viewModel::openHome
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}