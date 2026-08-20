@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.NavDisplay
import evola.shared.local.AppTheme
import evola.shared.local.SettingsRepository
import evola.composeapp.core.di.evolaModule
import evola.composeapp.core.database.rememberDatabaseDriverFactory
import evola.composeapp.core.navigation.AppNavContext
import evola.composeapp.core.navigation.AppRoute
import evola.composeapp.core.navigation.appNavigationModule
import evola.composeapp.core.analytics.rememberLogFileWriterFactory
import evola.composeapp.di.rememberFileTextExtractor
import evola.composeapp.feature.learning.ui.learningNavigationModule
import evola.composeapp.feature.vocabulary.ui.vocabularyNavigationModule
import evola.composeapp.main.materialsNavigationModule
import evola.composeapp.main.profileNavigationModule
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.BottomSheetDemoScreen
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.getOrNull
import evola.shared.goals.GoalsRepository
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider

// TEMPORARY preview switch for AppBottomSheetScaffold - flip to false (or delete this block +
// the branch below) once the component has been reviewed on-device.
private const val PREVIEW_BOTTOM_SHEET_DEMO = false

/**
 * Single-user, no-login app (serverless architecture): first run goes straight to Goal Setup; a
 * stored Anthropic key (entered in Profile) gates the AI features. There is no auth screen, token,
 * or account — the only gate to the main app is "does an active goal exist yet". See [AppRoute]
 * for the full splash/onboarding/main route model.
 */
@Composable
fun App() {
    val logFileWriterFactory = rememberLogFileWriterFactory()
    remember { EvolaLog.attachFileWriter(logFileWriterFactory.create()) }

    val driverFactory = rememberDatabaseDriverFactory()
    val secureStore = rememberSecureStore()
    val fileTextExtractor = rememberFileTextExtractor()

    KoinApplication(
        application = {
            modules(
                evolaModule(driverFactory, secureStore, fileTextExtractor),
                appNavigationModule,
                materialsNavigationModule,
                vocabularyNavigationModule,
                learningNavigationModule,
                profileNavigationModule,
            )
        },
    ) {
        val settingsRepository = koinInject<SettingsRepository>()
        val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

        EvolaTheme(appTheme = settings?.appTheme ?: AppTheme.SYSTEM) {
            if (PREVIEW_BOTTOM_SHEET_DEMO) {
                BottomSheetDemoScreen()
                return@EvolaTheme
            }

            val appBackStack = remember { mutableStateListOf<AppRoute>(AppRoute.Splash) }
            val resolvedTarget = remember { mutableStateOf<AppRoute?>(null) }
            val appNavContext = koinInject<AppNavContext>()
            appNavContext.backStack = appBackStack
            appNavContext.resolvedTarget = resolvedTarget

            val goalsRepository = koinInject<GoalsRepository>()
            // First-run routing: an active goal in the local DB → straight into the app; otherwise
            // onboarding. getActiveGoal() reads the on-device database, so this never touches the
            // network. Runs in parallel with the splash animation; AppRoute.Splash's own onFinished
            // decides when it's safe to hand off (its own minimum reveal time AND this result both
            // need to be ready).
            LaunchedEffect(Unit) {
                val goal = goalsRepository.getActiveGoal().getOrNull()
                if (goal != null) {
                    appNavContext.resolvedGoal = goal
                    resolvedTarget.value = AppRoute.Main
                } else {
                    resolvedTarget.value = AppRoute.OnboardingWelcome
                }
            }

            NavDisplay(
                backStack = appBackStack,
                onBack = {},
                entryProvider = koinEntryProvider(),
            )
        }
    }
}
