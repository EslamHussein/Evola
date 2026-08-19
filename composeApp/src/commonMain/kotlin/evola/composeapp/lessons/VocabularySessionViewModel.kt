package evola.composeapp.lessons

import androidx.lifecycle.viewModelScope
import evola.shared.local.AppSettings
import evola.shared.local.LocalSettingsRepository
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.android.StoreViewModel

/** [settings] is kept as a plain second `StateFlow` here (not folded into [VocabularySessionUiState])
 * - it's a genuinely independent reactive subscription (device-wide settings, unrelated lifecycle to
 * the session state machine) that the screen consumes alongside the FlowMVI-driven session state via
 * the ordinary `collectAsStateWithLifecycle()` path, same as before the FlowMVI migration. Drives the
 * session screen's invert-swipe direction and which of the two non-swipe checks (typed/multiple-choice)
 * are offered - see [evola.composeapp.main.SettingsScreen]. */
class VocabularySessionViewModel(
    source: VocabSessionSource,
    repository: VocabularyRepository,
    private val settingsRepository: LocalSettingsRepository,
) : StoreViewModel<VocabularySessionUiState, VocabularySessionIntent, Nothing>(VocabularySessionContainer(source, repository)) {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Dismisses the first-run swipe tutorial overlay for good - see [evola.composeapp.lessons.SwipeTutorialOverlay]. */
    fun markSwipeTutorialSeen() {
        viewModelScope.launch { settingsRepository.setHasSeenSwipeTutorial(true) }
    }
}
