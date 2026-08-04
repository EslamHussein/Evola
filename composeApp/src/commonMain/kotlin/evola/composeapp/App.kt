package evola.composeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.auth.ForgotPasswordScreen
import evola.composeapp.auth.ForgotPasswordViewModel
import evola.composeapp.auth.LoginScreen
import evola.composeapp.auth.LoginViewModel
import evola.composeapp.auth.RegisterScreen
import evola.composeapp.auth.RegisterViewModel
import evola.composeapp.auth.ResetPasswordScreen
import evola.composeapp.auth.ResetPasswordViewModel
import evola.composeapp.main.MainScreen
import evola.composeapp.onboarding.GoalSetupScreen
import evola.composeapp.onboarding.GoalSetupViewModel
import evola.composeapp.onboarding.WelcomeScreen
import evola.composeapp.theme.EvolaTheme
import evola.shared.auth.AuthTokens
import evola.shared.auth.AuthUser
import evola.shared.auth.HttpAuthRepository
import evola.shared.goals.Goal
import evola.shared.goals.HttpGoalsRepository
import evola.shared.lessons.HttpLessonsRepository
import evola.shared.materials.HttpMaterialsRepository
import evola.shared.vocabulary.HttpVocabularyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface AppScreen {
    data object Loading : AppScreen
    data object Login : AppScreen
    data object Register : AppScreen
    data object ForgotPassword : AppScreen
    data object ResetPassword : AppScreen
    data object OnboardingWelcome : AppScreen
    data object GoalSetup : AppScreen
    data class Main(val user: AuthUser, val goal: Goal) : AppScreen
}

@Composable
fun App() {
    EvolaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Loading) }
        var refreshToken by remember { mutableStateOf<String?>(null) }
        var accessToken by remember { mutableStateOf<String?>(null) }
        var currentUser by remember { mutableStateOf<AuthUser?>(null) }
        val sessionStorage = rememberSessionStorage()
        val authRepository = remember { HttpAuthRepository(baseUrl = defaultServerBaseUrl()) }
        val goalsRepository = remember { HttpGoalsRepository(baseUrl = defaultServerBaseUrl()) }
        val materialsRepository = remember { HttpMaterialsRepository(baseUrl = defaultServerBaseUrl()) }
        val vocabularyRepository = remember { HttpVocabularyRepository(baseUrl = defaultServerBaseUrl()) }
        val lessonsRepository = remember { HttpLessonsRepository(baseUrl = defaultServerBaseUrl()) }
        val coroutineScope = rememberCoroutineScope()

        // Onboarding isn't complete until both Welcome is shown AND a goal exists
        // (01_PRODUCT_SPEC.md §1.3) - the server only flips onboarding_completed as a side effect
        // of the first successful goal creation, so a true flag implies a goal already exists.
        suspend fun routePastLogin(user: AuthUser, token: String) {
            currentUser = user
            accessToken = token
            if (!user.onboardingCompleted) {
                screen = AppScreen.OnboardingWelcome
                return
            }
            val goal = try {
                goalsRepository.getActiveGoal(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            screen = if (goal != null) AppScreen.Main(user, goal) else AppScreen.OnboardingWelcome
        }

        val onAuthSuccess: (AuthTokens) -> Unit = { tokens ->
            refreshToken = tokens.refreshToken
            sessionStorage.saveRefreshToken(tokens.refreshToken)
            coroutineScope.launch { routePastLogin(tokens.user, tokens.accessToken) }
        }

        val onLogout: () -> Unit = {
            refreshToken?.let { token -> coroutineScope.launch { authRepository.logout(token) } }
            sessionStorage.clear()
            refreshToken = null
            accessToken = null
            currentUser = null
            screen = AppScreen.Login
        }

        // Silently restore a previous session on launch, so the user only logs in once.
        LaunchedEffect(Unit) {
            val storedRefreshToken = sessionStorage.loadRefreshToken()
            if (storedRefreshToken == null) {
                screen = AppScreen.Login
                return@LaunchedEffect
            }
            val restored = try {
                val freshAccessToken = authRepository.refresh(storedRefreshToken)
                val user = freshAccessToken?.let { authRepository.getCurrentUser(it) }
                if (freshAccessToken != null && user != null) freshAccessToken to user else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (restored != null) {
                val (freshAccessToken, user) = restored
                refreshToken = storedRefreshToken
                routePastLogin(user, freshAccessToken)
            } else {
                sessionStorage.clear()
                screen = AppScreen.Login
            }
        }

        when (val current = screen) {
            AppScreen.Loading -> {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

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
                BackHandler(onBack = { screen = AppScreen.Login })
                val viewModel = remember { RegisterViewModel(authRepository) }
                RegisterScreen(
                    viewModel = viewModel,
                    onRegistered = onAuthSuccess,
                    onBackToLogin = { screen = AppScreen.Login },
                )
            }

            AppScreen.ForgotPassword -> {
                BackHandler(onBack = { screen = AppScreen.Login })
                val viewModel = remember { ForgotPasswordViewModel(authRepository) }
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { screen = AppScreen.Login },
                    onHaveResetToken = { screen = AppScreen.ResetPassword },
                )
            }

            AppScreen.ResetPassword -> {
                BackHandler(onBack = { screen = AppScreen.Login })
                val viewModel = remember { ResetPasswordViewModel(authRepository) }
                ResetPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { screen = AppScreen.Login },
                )
            }

            AppScreen.OnboardingWelcome -> {
                WelcomeScreen(onContinue = { screen = AppScreen.GoalSetup })
            }

            AppScreen.GoalSetup -> {
                val token = accessToken
                if (token == null) {
                    screen = AppScreen.Login
                } else {
                    val viewModel = remember(token) { GoalSetupViewModel(goalsRepository, token) }
                    GoalSetupScreen(
                        viewModel = viewModel,
                        onGoalCreated = { goal ->
                            val user = currentUser
                            if (user != null) {
                                val updatedUser = user.copy(onboardingCompleted = true)
                                currentUser = updatedUser
                                screen = AppScreen.Main(updatedUser, goal)
                            }
                        },
                    )
                }
            }

            is AppScreen.Main -> {
                val token = accessToken
                if (token == null) {
                    screen = AppScreen.Login
                } else {
                    MainScreen(
                        user = current.user,
                        initialGoal = current.goal,
                        goalsRepository = goalsRepository,
                        materialsRepository = materialsRepository,
                        vocabularyRepository = vocabularyRepository,
                        lessonsRepository = lessonsRepository,
                        accessToken = token,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}
