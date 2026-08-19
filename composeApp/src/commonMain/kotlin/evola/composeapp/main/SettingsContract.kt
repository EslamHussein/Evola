package evola.composeapp.main

import evola.shared.local.AppSettings
import evola.shared.local.AppTheme
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

data class SettingsState(val settings: AppSettings = AppSettings()) : MVIState

sealed interface SettingsIntent : MVIIntent {
    data class SetDailyNewWordGoal(val value: Int) : SettingsIntent
    data class SetKeyboardExerciseEnabled(val value: Boolean) : SettingsIntent
    data class SetMultipleChoiceExerciseEnabled(val value: Boolean) : SettingsIntent
    data class SetInvertSwipe(val value: Boolean) : SettingsIntent
    data class SetTtsEnabled(val value: Boolean) : SettingsIntent
    data class SetTtsRate(val value: Float) : SettingsIntent
    data class SetTtsVoiceName(val value: String?) : SettingsIntent
    data class SetAutoPronounce(val value: Boolean) : SettingsIntent
    data class SetShowTranscription(val value: Boolean) : SettingsIntent
    data class SetNotificationsEnabled(val value: Boolean) : SettingsIntent
    data class SetReminderHour(val value: Int) : SettingsIntent
    data class SetSilentHoursStart(val value: Int) : SettingsIntent
    data class SetSilentHoursEnd(val value: Int) : SettingsIntent
    data class SetNotificationFrequencyLimitHours(val value: Int) : SettingsIntent
    data class SetAppTheme(val value: AppTheme) : SettingsIntent
    data class SetReducedMotion(val value: Boolean) : SettingsIntent
}
