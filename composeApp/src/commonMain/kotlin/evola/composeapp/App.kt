package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.shared.local.AppTheme
import evola.shared.local.LocalSettingsRepository
import evola.composeapp.di.GermanNounImportCoordinator
import evola.composeapp.di.evolaModule
import evola.composeapp.di.rememberDatabaseDriverFactory
import evola.composeapp.di.rememberFileTextExtractor
import evola.composeapp.di.rememberLogFileWriterFactory
import evola.composeapp.main.MainScreen
import evola.composeapp.onboarding.CategoryPickerScreen
import evola.composeapp.onboarding.DailyGoalPickerScreen
import evola.composeapp.onboarding.GoalSetupScreen
import evola.composeapp.onboarding.GoalSetupViewModel
import evola.composeapp.onboarding.NativeLanguageScreen
import evola.composeapp.onboarding.WelcomeScreen
import evola.composeapp.splash.SplashScreen
import evola.composeapp.splash.VocabDataImportScreen
import evola.composeapp.theme.EvolaTheme
import evola.composeapp.theme.components.BottomSheetDemoScreen
import evola.shared.core.EvolaLog
import evola.shared.core.getOrNull
import evola.shared.goals.Goal
import evola.shared.goals.GoalsRepository
import evola.shared.language.NativeLanguage
import evola.shared.vocabulary.GermanNounImportState
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// TEMPORARY preview switch for AppBottomSheetScaffold - flip to false (or delete this block +
// the branch below) once the component has been reviewed on-device.
private const val PREVIEW_BOTTOM_SHEET_DEMO = false

/**
 * Single-user, no-login app (serverless architecture): first run goes straight to Goal Setup; a
 * stored Anthropic key (entered in Profile) gates the AI features. There is no auth screen, token,
 * or account — the only gate to the main app is "does an active goal exist yet".
 */
private sealed interface AppScreen {
    data object Splash : AppScreen
    data object VocabDataImport : AppScreen
    data object OnboardingWelcome : AppScreen
    data object NativeLanguageSetup : AppScreen
    data class GoalSetup(val nativeLanguage: NativeLanguage) : AppScreen
    data class DailyGoalSetup(val goal: Goal) : AppScreen
    data class CategoryPicker(val goal: Goal) : AppScreen
    data class Main(val goal: Goal) : AppScreen
}

@Composable
fun App() {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }
    var resolvedTarget by remember { mutableStateOf<AppScreen?>(null) }

    val logFileWriterFactory = rememberLogFileWriterFactory()
    remember { EvolaLog.attachFileWriter(logFileWriterFactory.create()) }

    val driverFactory = rememberDatabaseDriverFactory()
    val secureStore = rememberSecureStore()
    val fileTextExtractor = rememberFileTextExtractor()

    KoinApplication(application = { modules(evolaModule(driverFactory, secureStore, fileTextExtractor)) }) {
        val settingsRepository = koinInject<LocalSettingsRepository>()
        val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

        EvolaTheme(appTheme = settings?.appTheme ?: AppTheme.SYSTEM) {
            if (PREVIEW_BOTTOM_SHEET_DEMO) {
                BottomSheetDemoScreen()
                return@EvolaTheme
            }

            val goalsRepository = koinInject<GoalsRepository>()
            val germanNounImportState by koinInject<GermanNounImportCoordinator>().state.collectAsStateWithLifecycle()

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
                        onFinished = {
                            screen = if (germanNounImportState is GermanNounImportState.Done) {
                                resolvedTarget ?: AppScreen.OnboardingWelcome
                            } else {
                                AppScreen.VocabDataImport
                            }
                        },
                    )
                }

                // Only reached on the very first launch (or after a future dataset update) - every
                // later launch has germanNounImportState already Done by the time Splash hands off,
                // so this screen is skipped entirely per the branch above.
                AppScreen.VocabDataImport -> {
                    VocabDataImportScreen(state = germanNounImportState)
                    LaunchedEffect(germanNounImportState, resolvedTarget) {
                        if (germanNounImportState is GermanNounImportState.Done) {
                            resolvedTarget?.let { screen = it }
                        }
                    }
                }

                AppScreen.OnboardingWelcome -> {
                    WelcomeScreen(onContinue = { screen = AppScreen.NativeLanguageSetup })
                }

                AppScreen.NativeLanguageSetup -> {
                    NativeLanguageScreen(onContinue = { language -> screen = AppScreen.GoalSetup(language) })
                }

                is AppScreen.GoalSetup -> {
                    val viewModel = koinViewModel<GoalSetupViewModel>()
                    GoalSetupScreen(
                        viewModel = viewModel,
                        nativeLanguage = current.nativeLanguage,
                        onGoalCreated = { goal -> screen = AppScreen.DailyGoalSetup(goal) },
                    )
                }

                is AppScreen.DailyGoalSetup -> {
                    DailyGoalPickerScreen(onContinue = { screen = AppScreen.CategoryPicker(current.goal) })
                }

                is AppScreen.CategoryPicker -> {
                    CategoryPickerScreen(goalId = current.goal.id, onContinue = { screen = AppScreen.Main(current.goal) })
                }

                is AppScreen.Main -> {
                    // LocalNativeLanguage is provided inside MainScreen itself (keyed on its own live
                    // `goal` state), not here - Profile can change the native language post-onboarding,
                    // and this `current.goal` capture never gets refreshed after the first load.
                    MainScreen(initialGoal = current.goal)
                }
            }
        }
    }
}
