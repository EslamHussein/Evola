package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import evola.composeapp.auth.ForgotPasswordScreen
import evola.composeapp.auth.ForgotPasswordViewModel
import evola.composeapp.auth.LoginScreen
import evola.composeapp.auth.LoginViewModel
import evola.composeapp.auth.RegisterScreen
import evola.composeapp.auth.RegisterViewModel
import evola.composeapp.auth.ResetPasswordScreen
import evola.composeapp.auth.ResetPasswordViewModel
import evola.composeapp.materials.AddMaterialScreen
import evola.composeapp.materials.AddMaterialViewModel
import evola.composeapp.materials.MaterialDetailScreen
import evola.composeapp.materials.MaterialDetailViewModel
import evola.composeapp.materials.MaterialsListScreen
import evola.composeapp.materials.MaterialsListViewModel
import evola.composeapp.theme.EvolaTheme
import evola.shared.auth.AuthTokens
import evola.shared.auth.HttpAuthRepository
import evola.shared.materials.HttpMaterialsRepository
import kotlinx.coroutines.launch

private sealed interface AppScreen {
    data object Login : AppScreen
    data object Register : AppScreen
    data object ForgotPassword : AppScreen
    data object ResetPassword : AppScreen
    data class MaterialsList(val userId: String) : AppScreen
    data class AddMaterial(val userId: String) : AppScreen
    data class MaterialDetail(val userId: String, val materialId: String) : AppScreen
}

@Composable
fun App() {
    EvolaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Login) }
        var refreshToken by remember { mutableStateOf<String?>(null) }
        val authRepository = remember { HttpAuthRepository(baseUrl = defaultServerBaseUrl()) }
        val materialsRepository = remember { HttpMaterialsRepository(baseUrl = defaultServerBaseUrl()) }
        val coroutineScope = rememberCoroutineScope()

        val onAuthSuccess: (AuthTokens) -> Unit = { tokens ->
            refreshToken = tokens.refreshToken
            screen = AppScreen.MaterialsList(tokens.user.id)
        }

        when (val current = screen) {
            AppScreen.Login -> {
                val viewModel = remember { LoginViewModel(authRepository) }
                LoginScreen(
                    viewModel = viewModel,
                    onLoggedIn = onAuthSuccess,
                    onForgotPassword = { screen = AppScreen.ForgotPassword },
                    onRegister = { screen = AppScreen.Register },
                )
            }

            AppScreen.Register -> {
                val viewModel = remember { RegisterViewModel(authRepository) }
                RegisterScreen(
                    viewModel = viewModel,
                    onRegistered = onAuthSuccess,
                    onBackToLogin = { screen = AppScreen.Login },
                )
            }

            AppScreen.ForgotPassword -> {
                val viewModel = remember { ForgotPasswordViewModel(authRepository) }
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { screen = AppScreen.Login },
                    onHaveResetToken = { screen = AppScreen.ResetPassword },
                )
            }

            AppScreen.ResetPassword -> {
                val viewModel = remember { ResetPasswordViewModel(authRepository) }
                ResetPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { screen = AppScreen.Login },
                )
            }

            is AppScreen.MaterialsList -> {
                val viewModel = remember(current.userId) { MaterialsListViewModel(current.userId, materialsRepository) }
                MaterialsListScreen(
                    viewModel = viewModel,
                    onAddMaterial = { screen = AppScreen.AddMaterial(current.userId) },
                    onOpenMaterial = { materialId -> screen = AppScreen.MaterialDetail(current.userId, materialId) },
                    onLogout = {
                        refreshToken?.let { token -> coroutineScope.launch { authRepository.logout(token) } }
                        refreshToken = null
                        screen = AppScreen.Login
                    },
                )
            }

            is AppScreen.AddMaterial -> {
                val viewModel = remember(current.userId) { AddMaterialViewModel(current.userId, materialsRepository) }
                AddMaterialScreen(
                    viewModel = viewModel,
                    onUploaded = { materialId -> screen = AppScreen.MaterialDetail(current.userId, materialId) },
                    onCancel = { screen = AppScreen.MaterialsList(current.userId) },
                )
            }

            is AppScreen.MaterialDetail -> {
                val viewModel = remember(current.materialId) { MaterialDetailViewModel(current.materialId, materialsRepository) }
                MaterialDetailScreen(
                    viewModel = viewModel,
                    onBack = { screen = AppScreen.MaterialsList(current.userId) },
                )
            }
        }
    }
}
