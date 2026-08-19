package evola.composeapp.main

import evola.shared.local.LocalSettingsRepository
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce
import pro.respawn.flowmvi.plugins.whileSubscribed

/** Thin reactive wrapper over [LocalSettingsRepository.settings] - every setter just upserts and
 * lets the repository's own Flow push the new value back, so [SettingsState.settings] is always the
 * source of truth rather than something this Container tracks separately. */
class SettingsContainer(private val repository: LocalSettingsRepository) : Container<SettingsState, SettingsIntent, Nothing> {

    override val store = store(initial = SettingsState()) {
        configure { name = "SettingsStore" }
        whileSubscribed {
            repository.settings.collect { settings -> updateState { SettingsState(settings) } }
        }
        reduce { intent ->
            when (intent) {
                is SettingsIntent.SetDailyNewWordGoal -> repository.setDailyNewWordGoal(intent.value)
                is SettingsIntent.SetKeyboardExerciseEnabled -> repository.setKeyboardExerciseEnabled(intent.value)
                is SettingsIntent.SetMultipleChoiceExerciseEnabled -> repository.setMultipleChoiceExerciseEnabled(intent.value)
                is SettingsIntent.SetInvertSwipe -> repository.setInvertSwipe(intent.value)
                is SettingsIntent.SetTtsEnabled -> repository.setTtsEnabled(intent.value)
                is SettingsIntent.SetTtsRate -> repository.setTtsRate(intent.value)
                is SettingsIntent.SetTtsVoiceName -> repository.setTtsVoiceName(intent.value)
                is SettingsIntent.SetAutoPronounce -> repository.setAutoPronounce(intent.value)
                is SettingsIntent.SetShowTranscription -> repository.setShowTranscription(intent.value)
                is SettingsIntent.SetNotificationsEnabled -> repository.setNotificationsEnabled(intent.value)
                is SettingsIntent.SetReminderHour -> repository.setReminderHour(intent.value)
                is SettingsIntent.SetSilentHoursStart -> repository.setSilentHoursStart(intent.value)
                is SettingsIntent.SetSilentHoursEnd -> repository.setSilentHoursEnd(intent.value)
                is SettingsIntent.SetNotificationFrequencyLimitHours -> repository.setNotificationFrequencyLimitHours(intent.value)
                is SettingsIntent.SetAppTheme -> repository.setAppTheme(intent.value)
                is SettingsIntent.SetReducedMotion -> repository.setReducedMotion(intent.value)
            }
        }
    }
}
