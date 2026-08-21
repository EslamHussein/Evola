package evola.shared.feature.profile.data

import evola.database.AppDatabase
import evola.database.entity.UserSettingEntity
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.profile.domain.AppSettings
import evola.shared.feature.profile.domain.AppTheme
import evola.shared.feature.profile.domain.DEFAULT_DAILY_NEW_WORD_GOAL
import evola.shared.feature.profile.domain.DEFAULT_NOTIFICATION_FREQUENCY_LIMIT_HOURS
import evola.shared.feature.profile.domain.DEFAULT_REMINDER_HOUR
import evola.shared.feature.profile.domain.DEFAULT_SILENT_HOURS_END
import evola.shared.feature.profile.domain.DEFAULT_SILENT_HOURS_START
import evola.shared.feature.profile.domain.DEFAULT_STREAK_FREEZES_AVAILABLE
import evola.shared.feature.profile.domain.DEFAULT_TTS_RATE
import evola.shared.feature.profile.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val KEY_DAILY_NEW_WORD_GOAL = "daily_new_word_goal"
private const val KEY_KEYBOARD_EXERCISE_ENABLED = "keyboard_exercise_enabled"
private const val KEY_MULTIPLE_CHOICE_EXERCISE_ENABLED = "multiple_choice_exercise_enabled"
private const val KEY_INVERT_SWIPE = "invert_swipe"
private const val KEY_TTS_ENABLED = "tts_enabled"
private const val KEY_TTS_RATE = "tts_rate"
private const val KEY_TTS_VOICE_NAME = "tts_voice_name"
private const val KEY_AUTO_PRONOUNCE = "auto_pronounce"
private const val KEY_SHOW_TRANSCRIPTION = "show_transcription"
private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val KEY_REMINDER_HOUR = "reminder_hour"
private const val KEY_SILENT_HOURS_START = "silent_hours_start"
private const val KEY_SILENT_HOURS_END = "silent_hours_end"
private const val KEY_NOTIFICATION_FREQUENCY_LIMIT_HOURS = "notification_frequency_limit_hours"
private const val KEY_LAST_NOTIFICATION_POSTED_AT_MILLIS = "last_notification_posted_at_millis"
private const val KEY_HAS_SEEN_SWIPE_TUTORIAL = "has_seen_swipe_tutorial"
private const val KEY_REDUCED_MOTION = "reduced_motion"
private const val KEY_APP_THEME = "app_theme"
private const val KEY_STREAK_FREEZES_AVAILABLE = "streak_freezes_available"

/**
 * Single-user local settings over the `user_settings` KV table (Settings.sq) - a plain key-value
 * table rather than one column per setting, so a new toggle never needs a schema migration.
 * Single-user: user is always [LOCAL_USER].
 */
class LocalSettingsRepository(private val db: AppDatabase) : SettingsRepository {

    override val settings: Flow<AppSettings> = db.settingsDao().all(LOCAL_USER)
        .map { rows -> rows.associate { it.key to it.value }.toAppSettings() }

    override suspend fun setDailyNewWordGoal(value: Int) = set(KEY_DAILY_NEW_WORD_GOAL, value.coerceAtLeast(1).toString())
    override suspend fun setKeyboardExerciseEnabled(value: Boolean) = set(KEY_KEYBOARD_EXERCISE_ENABLED, value.toString())
    override suspend fun setMultipleChoiceExerciseEnabled(value: Boolean) = set(KEY_MULTIPLE_CHOICE_EXERCISE_ENABLED, value.toString())
    override suspend fun setInvertSwipe(value: Boolean) = set(KEY_INVERT_SWIPE, value.toString())
    override suspend fun setTtsEnabled(value: Boolean) = set(KEY_TTS_ENABLED, value.toString())
    override suspend fun setTtsRate(value: Float) = set(KEY_TTS_RATE, value.toString())

    override suspend fun setTtsVoiceName(value: String?) {
        if (value == null) db.settingsDao().upsert(UserSettingEntity(LOCAL_USER, KEY_TTS_VOICE_NAME, "")) else set(KEY_TTS_VOICE_NAME, value)
    }
    override suspend fun setAutoPronounce(value: Boolean) = set(KEY_AUTO_PRONOUNCE, value.toString())
    override suspend fun setShowTranscription(value: Boolean) = set(KEY_SHOW_TRANSCRIPTION, value.toString())
    override suspend fun setNotificationsEnabled(value: Boolean) = set(KEY_NOTIFICATIONS_ENABLED, value.toString())
    override suspend fun setReminderHour(value: Int) = set(KEY_REMINDER_HOUR, value.coerceIn(0, 23).toString())
    override suspend fun setSilentHoursStart(value: Int) = set(KEY_SILENT_HOURS_START, value.coerceIn(0, 23).toString())
    override suspend fun setSilentHoursEnd(value: Int) = set(KEY_SILENT_HOURS_END, value.coerceIn(0, 23).toString())
    override suspend fun setNotificationFrequencyLimitHours(value: Int) = set(KEY_NOTIFICATION_FREQUENCY_LIMIT_HOURS, value.coerceIn(1, 24).toString())
    override suspend fun setHasSeenSwipeTutorial(value: Boolean) = set(KEY_HAS_SEEN_SWIPE_TUTORIAL, value.toString())
    override suspend fun setReducedMotion(value: Boolean) = set(KEY_REDUCED_MOTION, value.toString())
    override suspend fun setAppTheme(value: AppTheme) = set(KEY_APP_THEME, value.name)
    override suspend fun setStreakFreezesAvailable(value: Int) = set(KEY_STREAK_FREEZES_AVAILABLE, value.coerceAtLeast(0).toString())

    override suspend fun setLastNotificationPostedAtMillis(value: Long) = set(KEY_LAST_NOTIFICATION_POSTED_AT_MILLIS, value.toString())

    override fun current(): AppSettings = runBlocking { db.settingsDao().allOnce(LOCAL_USER) }.associate { it.key to it.value }.toAppSettings()

    private suspend fun set(key: String, value: String) {
        db.settingsDao().upsert(UserSettingEntity(LOCAL_USER, key, value))
    }

    private fun Map<String, String>.toAppSettings() = AppSettings(
        dailyNewWordGoal = this[KEY_DAILY_NEW_WORD_GOAL]?.toIntOrNull() ?: DEFAULT_DAILY_NEW_WORD_GOAL,
        keyboardExerciseEnabled = this[KEY_KEYBOARD_EXERCISE_ENABLED]?.toBooleanStrictOrNull() ?: true,
        multipleChoiceExerciseEnabled = this[KEY_MULTIPLE_CHOICE_EXERCISE_ENABLED]?.toBooleanStrictOrNull() ?: true,
        invertSwipe = this[KEY_INVERT_SWIPE]?.toBooleanStrictOrNull() ?: false,
        ttsEnabled = this[KEY_TTS_ENABLED]?.toBooleanStrictOrNull() ?: false,
        ttsRate = this[KEY_TTS_RATE]?.toFloatOrNull() ?: DEFAULT_TTS_RATE,
        ttsVoiceName = this[KEY_TTS_VOICE_NAME]?.ifBlank { null },
        autoPronounce = this[KEY_AUTO_PRONOUNCE]?.toBooleanStrictOrNull() ?: false,
        showTranscription = this[KEY_SHOW_TRANSCRIPTION]?.toBooleanStrictOrNull() ?: true,
        notificationsEnabled = this[KEY_NOTIFICATIONS_ENABLED]?.toBooleanStrictOrNull() ?: false,
        reminderHour = this[KEY_REMINDER_HOUR]?.toIntOrNull() ?: DEFAULT_REMINDER_HOUR,
        silentHoursStart = this[KEY_SILENT_HOURS_START]?.toIntOrNull() ?: DEFAULT_SILENT_HOURS_START,
        silentHoursEnd = this[KEY_SILENT_HOURS_END]?.toIntOrNull() ?: DEFAULT_SILENT_HOURS_END,
        notificationFrequencyLimitHours = this[KEY_NOTIFICATION_FREQUENCY_LIMIT_HOURS]?.toIntOrNull() ?: DEFAULT_NOTIFICATION_FREQUENCY_LIMIT_HOURS,
        lastNotificationPostedAtMillis = this[KEY_LAST_NOTIFICATION_POSTED_AT_MILLIS]?.toLongOrNull(),
        hasSeenSwipeTutorial = this[KEY_HAS_SEEN_SWIPE_TUTORIAL]?.toBooleanStrictOrNull() ?: false,
        reducedMotion = this[KEY_REDUCED_MOTION]?.toBooleanStrictOrNull() ?: false,
        appTheme = this[KEY_APP_THEME]?.let { raw -> AppTheme.entries.find { it.name == raw } } ?: AppTheme.SYSTEM,
        streakFreezesAvailable = this[KEY_STREAK_FREEZES_AVAILABLE]?.toIntOrNull() ?: DEFAULT_STREAK_FREEZES_AVAILABLE,
    )
}
