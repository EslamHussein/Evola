package evola.composeapp.reminders

import androidx.compose.runtime.Composable

/** Schedules/cancels the daily "words are due for review" local notification (Settings >
 * Notifications). Android: a `WorkManager` `PeriodicWorkRequest` that checks the real due-count and
 * only posts when it's non-zero. iOS: a repeating `UNCalendarNotificationTrigger` with a static
 * body - iOS can't run arbitrary Kotlin to compute a live count inside the trigger, a deliberate,
 * disclosed platform asymmetry. */
expect class ReminderScheduler {
    fun scheduleDaily(hour: Int)
    fun cancel()
}

/** Composable provider mirroring [evola.composeapp.core.database.rememberDatabaseDriverFactory]/
 * [evola.composeapp.speech.rememberSpeechService]. */
@Composable
expect fun rememberReminderScheduler(): ReminderScheduler

/** Returns a launcher for the platform notification-permission prompt (Android 13+
 * `POST_NOTIFICATIONS`; iOS alert/sound authorization). [onResult] fires once with whether
 * permission was granted - on Android below API 33, or if already granted, it fires immediately
 * without showing anything. */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
