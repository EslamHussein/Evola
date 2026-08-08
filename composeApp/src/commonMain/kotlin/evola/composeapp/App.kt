package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import evola.composeapp.di.AppModule
import evola.composeapp.di.rememberDatabaseDriverFactory
import evola.composeapp.di.rememberFileTextExtractor
import evola.composeapp.main.MainScreen
import evola.composeapp.onboarding.GoalSetupScreen
import evola.composeapp.onboarding.GoalSetupViewModel
import evola.composeapp.onboarding.NativeLanguageScreen
import evola.composeapp.onboarding.WelcomeScreen
import evola.composeapp.splash.SplashScreen
import evola.composeapp.theme.EvolaTheme
import evola.shared.core.getOrNull
import evola.shared.goals.Goal
import evola.shared.language.NativeLanguage

/**
 * Single-user, no-login app (serverless architecture): first run goes straight to Goal Setup; a
 * stored Anthropic key (entered in Profile) gates the AI features. There is no auth screen, token,
 * or account — the only gate to the main app is "does an active goal exist yet".
 */
private sealed interface AppScreen {
    data object Splash : AppScreen
    data object OnboardingWelcome : AppScreen
    data object NativeLanguageSetup : AppScreen
    data class GoalSetup(val nativeLanguage: NativeLanguage) : AppScreen
    data class Main(val goal: Goal) : AppScreen
}

@Composable
fun App() {
    EvolaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }
        var resolvedTarget by remember { mutableStateOf<AppScreen?>(null) }

        val driverFactory = rememberDatabaseDriverFactory()
        val secureStore = rememberSecureStore()
        val fileTextExtractor = rememberFileTextExtractor()
        val appModule = remember { AppModule(driverFactory, secureStore, fileTextExtractor) }
        val goalsRepository = appModule.goalsRepository

        // First-run routing: an active goal in the local DB → straight into the app; otherwise
        // onboarding. getActiveGoal() reads the on-device database, so this never touches the network.
        // Runs in parallel with the splash animation; SplashScreen itself decides when it's safe to
        // hand off (its own minimum reveal time AND this result both need to be ready).
        LaunchedEffect(Unit) {
            val goal = goalsRepository.getActiveGoal().getOrNull()
            resolvedTarget = if (goal != null) AppScreen.Main(goal) else AppScreen.OnboardingWelcome
        }

        when (val current = screen) {
            AppScreen.Splash -> {
                SplashScreen(
                    dataReady = resolvedTarget != null,
                    onFinished = { screen = resolvedTarget ?: AppScreen.OnboardingWelcome },
                )
            }

            AppScreen.OnboardingWelcome -> {
                WelcomeScreen(onContinue = { screen = AppScreen.NativeLanguageSetup })
            }

            AppScreen.NativeLanguageSetup -> {
                NativeLanguageScreen(onContinue = { language -> screen = AppScreen.GoalSetup(language) })
            }

            is AppScreen.GoalSetup -> {
                val viewModel = remember { GoalSetupViewModel(goalsRepository) }
                GoalSetupScreen(
                    viewModel = viewModel,
                    nativeLanguage = current.nativeLanguage,
                    onGoalCreated = { goal -> screen = AppScreen.Main(goal) },
                )
            }

            is AppScreen.Main -> {
                // LocalNativeLanguage is provided inside MainScreen itself (keyed on its own live
                // `goal` state), not here - Profile can change the native language post-onboarding,
                // and this `current.goal` capture never gets refreshed after the first load.
                MainScreen(
                    initialGoal = current.goal,
                    goalsRepository = goalsRepository,
                    materialsRepository = appModule.materialsRepository,
                    vocabularyRepository = appModule.vocabularyRepository,
                    lessonsRepository = appModule.lessonsRepository,
                    grammarRepository = appModule.grammarRepository,
                )
            }
        }
    }
}
