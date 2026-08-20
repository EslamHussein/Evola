package evola.composeapp.feature.profile.vm

import androidx.lifecycle.ViewModel
import evola.shared.feature.profile.domain.AppTheme
import evola.shared.feature.profile.domain.SettingsRepository
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Thin reactive wrapper over [SettingsRepository.settings] - every setter just upserts and
 * lets the repository's own Flow push the new value back, so [SettingsState.settings] is always the
 * source of truth rather than something this ViewModel tracks separately. */
class SettingsViewModel(private val repository: SettingsRepository) :
    ViewModel(), OrbitContainerHost<SettingsState, SettingsState, Nothing> {

    override val container = orbitContainer<SettingsState, Nothing>(SettingsState(), onCreate = {
        repository.settings.collect { settings -> reduce { SettingsState(settings) } }
    })

    fun setDailyNewWordGoal(value: Int) = intent { repository.setDailyNewWordGoal(value) }
    fun setKeyboardExerciseEnabled(value: Boolean) = intent { repository.setKeyboardExerciseEnabled(value) }
    fun setMultipleChoiceExerciseEnabled(value: Boolean) = intent { repository.setMultipleChoiceExerciseEnabled(value) }
    fun setInvertSwipe(value: Boolean) = intent { repository.setInvertSwipe(value) }
    fun setTtsEnabled(value: Boolean) = intent { repository.setTtsEnabled(value) }
    fun setTtsRate(value: Float) = intent { repository.setTtsRate(value) }
    fun setTtsVoiceName(value: String?) = intent { repository.setTtsVoiceName(value) }
    fun setAutoPronounce(value: Boolean) = intent { repository.setAutoPronounce(value) }
    fun setShowTranscription(value: Boolean) = intent { repository.setShowTranscription(value) }
    fun setNotificationsEnabled(value: Boolean) = intent { repository.setNotificationsEnabled(value) }
    fun setReminderHour(value: Int) = intent { repository.setReminderHour(value) }
    fun setSilentHoursStart(value: Int) = intent { repository.setSilentHoursStart(value) }
    fun setSilentHoursEnd(value: Int) = intent { repository.setSilentHoursEnd(value) }
    fun setNotificationFrequencyLimitHours(value: Int) = intent { repository.setNotificationFrequencyLimitHours(value) }
    fun setAppTheme(value: AppTheme) = intent { repository.setAppTheme(value) }
    fun setReducedMotion(value: Boolean) = intent { repository.setReducedMotion(value) }
}
