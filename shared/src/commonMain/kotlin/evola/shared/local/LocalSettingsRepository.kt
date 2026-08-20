package evola.shared.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import evola.shared.core.common.LOCAL_USER
import evola.shared.db.EvolaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

const val DEFAULT_DAILY_NEW_WORD_GOAL = 8
const val DEFAULT_TTS_RATE = 1.0f
const val DEFAULT_REMINDER_HOUR = 18
const val DEFAULT_SILENT_HOURS_START = 22
const val DEFAULT_SILENT_HOURS_END = 8
const val DEFAULT_NOTIFICATION_FREQUENCY_LIMIT_HOURS = 2
/** Reword's "Streak freeze" starting bank - a fixed grant, not replenished automatically (no
 * weekly/XP-based regrant exists here, unlike Duolingo) - kept simple on purpose. */
const val DEFAULT_STREAK_FREEZES_AVAILABLE = 2

/** Reword's Settings > Appearance - System follows the OS light/dark setting, the other two force it. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Every user-tunable knob this app has, defaulted so an absent row (fresh install, or a setting
 * never touched) behaves exactly like the app did before Settings existed - both exercise types on,
 * TTS/notifications off until explicitly enabled (they're new capabilities, not new defaults). */
data class AppSettings(
    val dailyNewWordGoal: Int = DEFAULT_DAILY_NEW_WORD_GOAL,
    val keyboardExerciseEnabled: Boolean = true,
    val multipleChoiceExerciseEnabled: Boolean = true,
    val invertSwipe: Boolean = false,
    val ttsEnabled: Boolean = false,
    val ttsRate: Float = DEFAULT_TTS_RATE,
    /** Reword's "Robot voice" picker - one of [evola.composeapp.speech.SpeechService.
     * availableVoiceNames]'s results, or null for the platform/engine default. */
    val ttsVoiceName: String? = null,
    /** Reword's "Automatically pronounce English words" - auto-speaks a card's term as soon as it
     * appears, on top of (not instead of) the always-available manual tap-to-hear button. */
    val autoPronounce: Boolean = false,
    /** Reword's "Show transcription" - IPA display on/off, both in a session and in the vocabulary
     * list. Default on since Evola already extracts IPA when available and showed it unconditionally
     * before this toggle existed. */
    val showTranscription: Boolean = true,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    /** Reword's "Silent mode" - no review-reminder notification fires with the current hour inside
     * [silentHoursStart]..[silentHoursEnd] (wrapping past midnight, e.g. 22..8). Checked by the
     * Android Worker before every post; see [LocalSettingsRepository] class doc for why this has no
     * real iOS equivalent. */
    val silentHoursStart: Int = DEFAULT_SILENT_HOURS_START,
    val silentHoursEnd: Int = DEFAULT_SILENT_HOURS_END,
    /** Reword's "Notification frequency limit" - the Android Worker won't post again until this
     * many hours have passed since [lastNotificationPostedAtMillis]. */
    val notificationFrequencyLimitHours: Int = DEFAULT_NOTIFICATION_FREQUENCY_LIMIT_HOURS,
    val lastNotificationPostedAtMillis: Long? = null,
    /** Gates the vocabulary session's first-run swipe tutorial overlay - shown once, ever, then
     * flipped true the first time a session screen composes past it. */
    val hasSeenSwipeTutorial: Boolean = false,
    /** Reword's "Reduce motion" - session card transitions/animations become instant when true. */
    val reducedMotion: Boolean = false,
    val appTheme: AppTheme = AppTheme.SYSTEM,
    /** Reword's "Streak freeze" bank - see [evola.shared.local.LocalGoalsRepository]'s use of
     * [evola.shared.db.EvolaDatabase.streak_freeze_datesQueries] for how a freeze gets spent. */
    val streakFreezesAvailable: Int = DEFAULT_STREAK_FREEZES_AVAILABLE,
)

/** True if [hour] (0-23, local time) falls inside this user's configured silent-hours window -
 * wraps past midnight (e.g. start=22, end=8 means 22:00-23:59 AND 00:00-07:59 are silent). Pure,
 * so the Android reminder Worker (and any test) can check it without touching a clock. */
fun AppSettings.isWithinSilentHours(hour: Int): Boolean =
    if (silentHoursStart <= silentHoursEnd) hour in silentHoursStart until silentHoursEnd else hour >= silentHoursStart || hour < silentHoursEnd

/** True if a notification was posted more recently than [notificationFrequencyLimitHours] ago -
 * the Android Worker skips posting again while this is true. Never limited if nothing's been
 * posted yet. */
fun AppSettings.isWithinNotificationFrequencyLimit(nowMillis: Long): Boolean {
    val last = lastNotificationPostedAtMillis ?: return false
    return nowMillis - last < notificationFrequencyLimitHours * 3_600_000L
}

/**
 * Every user-tunable knob this app has, reactively and single-user. [settings] is a live Flow so
 * every consumer (Settings screen, the vocabulary session's exercise toggles/invert-swipe/TTS
 * gate, Home's daily-goal readout, the reminder scheduler) reacts to a change immediately without
 * polling. Every setter is a plain suspend upsert - callers don't need to read-modify-write, since
 * [settings] always reflects the latest row.
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDailyNewWordGoal(value: Int)
    suspend fun setKeyboardExerciseEnabled(value: Boolean)
    suspend fun setMultipleChoiceExerciseEnabled(value: Boolean)
    suspend fun setInvertSwipe(value: Boolean)
    suspend fun setTtsEnabled(value: Boolean)
    suspend fun setTtsRate(value: Float)

    /** Null clears back to the platform/engine default. */
    suspend fun setTtsVoiceName(value: String?)
    suspend fun setAutoPronounce(value: Boolean)
    suspend fun setShowTranscription(value: Boolean)
    suspend fun setNotificationsEnabled(value: Boolean)
    suspend fun setReminderHour(value: Int)
    suspend fun setSilentHoursStart(value: Int)
    suspend fun setSilentHoursEnd(value: Int)
    suspend fun setNotificationFrequencyLimitHours(value: Int)
    suspend fun setHasSeenSwipeTutorial(value: Boolean)
    suspend fun setReducedMotion(value: Boolean)
    suspend fun setAppTheme(value: AppTheme)
    suspend fun setStreakFreezesAvailable(value: Int)

    /** Written by the reminder Worker itself right after a successful post - not a user-facing
     * setting, just reuses this table since it's the app's only KV store. */
    suspend fun setLastNotificationPostedAtMillis(value: Long)

    /** One-shot, non-reactive read - for call sites that aren't already Compose-collecting
     * [settings] as a Flow: the vocabulary session's queue assembly (a plain suspend function, not a
     * composable) and the reminder Worker (no Compose scope at all). */
    fun current(): AppSettings
}

/**
 * Single-user local settings over the `user_settings` KV table (Settings.sq) - a plain key-value
 * table rather than one column per setting, so a new toggle never needs a schema migration.
 * Single-user: user is always [LOCAL_USER].
 */
class LocalSettingsRepository(private val db: EvolaDatabase) : SettingsRepository {

    override val settings: Flow<AppSettings> = db.settingsQueries.all(LOCAL_USER)
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.associate { it.key to it.value_ }.toAppSettings() }

    override suspend fun setDailyNewWordGoal(value: Int) = set(KEY_DAILY_NEW_WORD_GOAL, value.coerceAtLeast(1).toString())
    override suspend fun setKeyboardExerciseEnabled(value: Boolean) = set(KEY_KEYBOARD_EXERCISE_ENABLED, value.toString())
    override suspend fun setMultipleChoiceExerciseEnabled(value: Boolean) = set(KEY_MULTIPLE_CHOICE_EXERCISE_ENABLED, value.toString())
    override suspend fun setInvertSwipe(value: Boolean) = set(KEY_INVERT_SWIPE, value.toString())
    override suspend fun setTtsEnabled(value: Boolean) = set(KEY_TTS_ENABLED, value.toString())
    override suspend fun setTtsRate(value: Float) = set(KEY_TTS_RATE, value.toString())

    override suspend fun setTtsVoiceName(value: String?) {
        if (value == null) db.settingsQueries.upsert(LOCAL_USER, KEY_TTS_VOICE_NAME, "") else set(KEY_TTS_VOICE_NAME, value)
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

    override fun current(): AppSettings = db.settingsQueries.all(LOCAL_USER).executeAsList().associate { it.key to it.value_ }.toAppSettings()

    private fun set(key: String, value: String) {
        db.settingsQueries.upsert(LOCAL_USER, key, value)
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
