package evola.composeapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import evola.composeapp.R
import evola.database.DatabaseFactory
import evola.database.create
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.srs.computeStreak
import evola.shared.todayLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Reword's home-screen widget - streak + due-word count, refreshed on the system's own
 * [android.appwidget.AppWidgetProviderInfo] schedule (`widget_home_info.xml`) or on-demand
 * ([AppWidgetManager.EXTRA_APPWIDGET_IDS]). No Compose scope here, same as [evola.composeapp.
 * reminders.ReviewReminderWorker] - opens its own short-lived database connection rather than
 * sharing the composition root's Koin-managed instance, since the widget's process may not have
 * the app running at all. Deliberately reads streak/due-count directly (not via
 * [evola.shared.feature.onboarding.domain.GoalsRepository.getProgress]) to avoid also triggering that call's
 * achievement-unlock side effect from a background widget refresh. iOS has no equivalent - Compose
 * Multiplatform/Kotlin can't back a WidgetKit extension, which is a separate Swift target Xcode-side
 * (see docs/ROADMAP.md for this disclosed platform asymmetry). */
class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = DatabaseFactory(context).create()
            val today = LocalDate.parse(todayLocalDate())
            val activityDates = db.activityDao().completedDates(LOCAL_USER)
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            val frozenDates = db.activityDao().frozenDates(LOCAL_USER)
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            val streak = computeStreak((activityDates + frozenDates).toSet(), today)
            val dueCount = db.vocabularyDao().dueCountForUser(LOCAL_USER, System.currentTimeMillis())

            appWidgetIds.forEach { appWidgetId -> updateWidget(context, appWidgetManager, appWidgetId, streak, dueCount) }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, streak: Int, dueCount: Long) {
        val views = RemoteViews(context.packageName, R.layout.widget_home)
        views.setTextViewText(R.id.widget_streak_text, if (streak == 1) "1 day streak" else "$streak day streak")
        views.setTextViewText(
            R.id.widget_due_text,
            if (dueCount == 0L) "No words due" else if (dueCount == 1L) "1 word due" else "$dueCount words due",
        )
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        openAppIntent?.let {
            val pendingIntent = PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        /** Called after any action that changes the streak/due count (e.g. finishing a session) to
         * refresh every placed instance immediately, rather than waiting for the next scheduled
         * `updatePeriodMillis` tick. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    android.content.Intent(context, HomeWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            }
        }
    }
}
