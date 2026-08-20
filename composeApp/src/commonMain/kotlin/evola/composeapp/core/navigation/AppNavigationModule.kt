@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.core.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.core.di.GermanNounImportCoordinator
import evola.composeapp.main.MainScreen
import evola.composeapp.feature.onboarding.ui.CategoryPickerScreen
import evola.composeapp.feature.onboarding.ui.DailyGoalPickerScreen
import evola.composeapp.feature.onboarding.ui.GoalSetupScreen
import evola.composeapp.feature.onboarding.vm.GoalSetupViewModel
import evola.composeapp.feature.onboarding.ui.NativeLanguageScreen
import evola.composeapp.feature.onboarding.ui.WelcomeScreen
import evola.composeapp.feature.onboarding.ui.SplashScreen
import evola.composeapp.feature.onboarding.ui.VocabDataImportScreen
import evola.shared.feature.vocabulary.domain.GermanNounImportState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

/** Declares every [AppRoute] once, same rationale as [evola.composeapp.feature.materials.ui.materialsNavigationModule].
 * Every transition is a full stack replace, not a push - see [AppRoute]'s doc comment for why. */
val appNavigationModule = module {
    single { AppNavContext() }

    navigation<AppRoute.Splash> {
        val context = koinInject<AppNavContext>()
        val germanNounImportState by koinInject<GermanNounImportCoordinator>().state.collectAsStateWithLifecycle()
        SplashScreen(
            dataReady = context.resolvedTarget.value != null,
            onFinished = {
                val target = if (germanNounImportState is GermanNounImportState.Done) {
                    context.resolvedTarget.value ?: AppRoute.OnboardingWelcome
                } else {
                    AppRoute.VocabDataImport
                }
                context.backStack.clear()
                context.backStack.add(target)
            },
        )
    }

    // Only reached on the very first launch (or after a future dataset update) - every later
    // launch has germanNounImportState already Done by the time Splash hands off, so this route is
    // skipped entirely per the branch above.
    navigation<AppRoute.VocabDataImport> {
        val context = koinInject<AppNavContext>()
        val germanNounImportState by koinInject<GermanNounImportCoordinator>().state.collectAsStateWithLifecycle()
        VocabDataImportScreen(state = germanNounImportState)
        LaunchedEffect(germanNounImportState, context.resolvedTarget.value) {
            if (germanNounImportState is GermanNounImportState.Done) {
                context.resolvedTarget.value?.let {
                    context.backStack.clear()
                    context.backStack.add(it)
                }
            }
        }
    }

    navigation<AppRoute.OnboardingWelcome> {
        val context = koinInject<AppNavContext>()
        WelcomeScreen(
            onContinue = {
                context.backStack.clear()
                context.backStack.add(AppRoute.NativeLanguageSetup)
            },
        )
    }

    navigation<AppRoute.NativeLanguageSetup> {
        val context = koinInject<AppNavContext>()
        NativeLanguageScreen(
            onContinue = { language ->
                context.backStack.clear()
                context.backStack.add(AppRoute.GoalSetup(language))
            },
        )
    }

    navigation<AppRoute.GoalSetup> { route ->
        val context = koinInject<AppNavContext>()
        val viewModel = koinViewModel<GoalSetupViewModel>()
        GoalSetupScreen(
            viewModel = viewModel,
            nativeLanguage = route.nativeLanguage,
            onGoalCreated = { goal ->
                context.resolvedGoal = goal
                context.backStack.clear()
                context.backStack.add(AppRoute.DailyGoalSetup)
            },
        )
    }

    navigation<AppRoute.DailyGoalSetup> {
        val context = koinInject<AppNavContext>()
        DailyGoalPickerScreen(
            onContinue = {
                context.backStack.clear()
                context.backStack.add(AppRoute.CategoryPicker)
            },
        )
    }

    navigation<AppRoute.CategoryPicker> {
        val context = koinInject<AppNavContext>()
        CategoryPickerScreen(
            goalId = context.resolvedGoal!!.id,
            onContinue = {
                context.backStack.clear()
                context.backStack.add(AppRoute.Main)
            },
        )
    }

    // LocalNativeLanguage is provided inside MainScreen itself (keyed on its own live `goal`
    // state), not here - Profile can change the native language post-onboarding, and
    // resolvedGoal never gets refreshed after this first read.
    navigation<AppRoute.Main> {
        val context = koinInject<AppNavContext>()
        MainScreen(initialGoal = context.resolvedGoal!!)
    }
}
