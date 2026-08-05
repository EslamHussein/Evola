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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.di.AppModule
import evola.composeapp.di.rememberDatabaseDriverFactory
import evola.composeapp.di.rememberFileTextExtractor
import evola.composeapp.main.MainScreen
import evola.composeapp.onboarding.GoalSetupScreen
import evola.composeapp.onboarding.GoalSetupViewModel
import evola.composeapp.onboarding.WelcomeScreen
import evola.composeapp.theme.EvolaTheme
import evola.shared.core.getOrNull
import evola.shared.goals.Goal

/**
 * Single-user, no-login app (serverless architecture): first run goes straight to Goal Setup; a
 * stored Anthropic key (entered in Profile) gates the AI features. There is no auth screen, token,
 * or account — the only gate to the main app is "does an active goal exist yet".
 */
private sealed interface AppScreen {
    data object Loading : AppScreen
    data object OnboardingWelcome : AppScreen
    data object GoalSetup : AppScreen
    data class Main(val goal: Goal) : AppScreen
}

@Composable
fun App() {
    EvolaTheme {
        var screen by remember { mutableStateOf<AppScreen>(AppScreen.Loading) }

        val driverFactory = rememberDatabaseDriverFactory()
        val secureStore = rememberSecureStore()
        val fileTextExtractor = rememberFileTextExtractor()
        val appModule = remember { AppModule(driverFactory, secureStore, fileTextExtractor) }
        val goalsRepository = appModule.goalsRepository

        // First-run routing: an active goal in the local DB → straight into the app; otherwise
        // onboarding. getActiveGoal() reads the on-device database, so this never touches the network.
        LaunchedEffect(Unit) {
            val goal = goalsRepository.getActiveGoal().getOrNull()
            screen = if (goal != null) AppScreen.Main(goal) else AppScreen.OnboardingWelcome
        }

        when (val current = screen) {
            AppScreen.Loading -> {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            AppScreen.OnboardingWelcome -> {
                WelcomeScreen(onContinue = { screen = AppScreen.GoalSetup })
            }

            AppScreen.GoalSetup -> {
                val viewModel = remember { GoalSetupViewModel(goalsRepository) }
                GoalSetupScreen(
                    viewModel = viewModel,
                    onGoalCreated = { goal -> screen = AppScreen.Main(goal) },
                )
            }

            is AppScreen.Main -> {
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
