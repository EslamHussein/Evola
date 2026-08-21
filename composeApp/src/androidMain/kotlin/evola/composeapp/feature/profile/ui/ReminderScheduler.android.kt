package evola.composeapp.feature.profile.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import evola.composeapp.core.database.DatabaseDriverFactory
import evola.database.DatabaseFactory
import evola.database.create
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.profile.data.LocalSettingsRepository
import evola.shared.feature.profile.domain.isWithinNotificationFrequencyLimit
import evola.shared.feature.profile.domain.isWithinSilentHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "evola_review_reminder"
private const val CHANNEL_ID = "review_reminders"
private const val NOTIFICATION_ID = 1

actual class ReminderScheduler(private val context: Context) {
    actual fun scheduleDaily(hour: Int) {
        ensureChannel()
        val initialDelayMinutes = minutesUntil(hour)
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    actual fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_NAME)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A daily nudge when vocabulary words are due for review"
        }
        manager.createNotificationChannel(channel)
    }

    /** Minutes from now until the next occurrence of [hour]:00 local time - today's if it hasn't
     * passed yet, otherwise tomorrow's. */
    private fun minutesUntil(hour: Int): Long {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val nowLocal = now.toLocalDateTime(zone)
        val minutesNow = nowLocal.hour * 60 + nowLocal.minute
        val targetMinutes = hour * 60
        val diff = targetMinutes - minutesNow
        return (if (diff <= 0) diff + 24 * 60 else diff).toLong()
    }
}

@Composable
actual fun rememberReminderScheduler(): ReminderScheduler {
    val context = LocalContext.current
    return remember { ReminderScheduler(context) }
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val onResultState = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResultState.value(granted)
    }
    return {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResultState.value(true)
        } else if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            onResultState.value(true)
        } else {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Runs once a day (see [ReminderScheduler.scheduleDaily]) - has no Compose scope, so it opens its
 * own short-lived database connection the same way [evola.composeapp.App]'s composition root does,
 * rather than trying to share [evola.composeapp.core.di.evolaModule]'s long-lived instance across a
 * process that may not even have the app in memory. */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = EvolaDatabase(DatabaseDriverFactory(applicationContext).create())
        val roomDb = DatabaseFactory(applicationContext).create()
        val settingsRepository = LocalSettingsRepository(roomDb)
        val settings = settingsRepository.current()
        val now = Clock.System.now()
        val currentHour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour

        // Settings > Silent mode / Notification frequency limit - both checked here (not just
        // relied on via the once-daily schedule) so an edited reminder hour, a DST shift, or a
        // manually-triggered run can never post outside what the user actually configured.
        if (settings.isWithinSilentHours(currentHour) || settings.isWithinNotificationFrequencyLimit(now.toEpochMilliseconds())) {
            return@withContext Result.success()
        }

        val dueCount = db.vocabularyQueries.dueCountForUser(LOCAL_USER, now.toEpochMilliseconds()).executeAsOne()
        if (dueCount > 0) {
            postNotification(dueCount)
            settingsRepository.setLastNotificationPostedAtMillis(now.toEpochMilliseconds())
        }
        Result.success()
    }

    private fun postNotification(dueCount: Long) {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        // Resolved generically via the package's own launcher intent rather than a hardcoded
        // Activity class - :composeApp doesn't (and shouldn't) depend on :androidApp, which is
        // where MainActivity actually lives.
        val openAppIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pendingIntent = openAppIntent?.let {
            android.app.PendingIntent.getActivity(
                applicationContext, 0, it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val body = if (dueCount == 1L) "1 word is due for review" else "$dueCount words are due for review"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time to review")
            .setContentText(body)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }
}
