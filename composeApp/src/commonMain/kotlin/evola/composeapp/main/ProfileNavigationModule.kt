@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.main

import androidx.compose.runtime.getValue
import evola.composeapp.reminders.rememberNotificationPermissionRequester
import evola.composeapp.reminders.rememberReminderScheduler
import evola.composeapp.speech.rememberSpeechService
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.orbitmvi.orbit.compose.collectAsState

/** Declares the Profile tab's two [ProfileRoute] destinations, same rationale as
 * [materialsNavigationModule]. */
val profileNavigationModule = module {
    single { ProfileNavContext() }

    navigation<ProfileRoute.Main> {
        val context = koinInject<ProfileNavContext>()
        val viewModel = koinViewModel<ProfileViewModel>(key = context.goal.id) { parametersOf(context.goal.id) }
        ProfileScreen(
            goal = context.goal,
            viewModel = viewModel,
            onGoalUpdated = context.onGoalUpdated,
            onOpenSettings = { context.backStack.add(ProfileRoute.Settings) },
        )
    }

    navigation<ProfileRoute.Settings> {
        val context = koinInject<ProfileNavContext>()
        val settingsViewModel = koinViewModel<SettingsViewModel>()
        val reminderScheduler = rememberReminderScheduler()
        val currentSettingsState by settingsViewModel.collectAsState()
        val requestNotificationPermission = rememberNotificationPermissionRequester { granted ->
            if (granted) {
                reminderScheduler.scheduleDaily(currentSettingsState.settings.reminderHour)
            } else {
                // Permission denied - the toggle stays visually on (matching the OS's own
                // "you can flip this in system settings later" convention) but nothing is
                // actually scheduled until the user grants it from system settings.
                settingsViewModel.setNotificationsEnabled(false)
            }
        }
        val speechService = rememberSpeechService()
        SettingsScreen(
            viewModel = settingsViewModel,
            speechService = speechService,
            onBack = { context.backStack.removeLastOrNull() },
            onNotificationsToggled = { enabled ->
                if (enabled) requestNotificationPermission() else reminderScheduler.cancel()
            },
        )
    }
}
