package evola.composeapp.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

private const val REMINDER_IDENTIFIER = "evola_review_reminder"

/** iOS can't run arbitrary Kotlin inside a notification trigger to compute a live due-count the
 * way the Android Worker does, so this is a static, always-the-same-wording daily reminder - a
 * deliberate, disclosed platform asymmetry rather than an oversight. */
actual class ReminderScheduler {
    actual fun scheduleDaily(hour: Int) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_IDENTIFIER))

        val content = UNMutableNotificationContent().apply {
            setTitle("Time to review")
            setBody("Some of your German words are due for review.")
        }
        val dateComponents = NSDateComponents().apply { this.hour = hour.toLong(); this.minute = 0 }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(dateComponents, repeats = true)
        val request = UNNotificationRequest.requestWithIdentifier(REMINDER_IDENTIFIER, content, trigger)
        center.addNotificationRequest(request, null)
    }

    actual fun cancel() {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_IDENTIFIER))
    }
}

@Composable
actual fun rememberReminderScheduler(): ReminderScheduler = remember { ReminderScheduler() }

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val onResultState = rememberUpdatedState(onResult)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            onResultState.value(granted)
        }
    }
}
