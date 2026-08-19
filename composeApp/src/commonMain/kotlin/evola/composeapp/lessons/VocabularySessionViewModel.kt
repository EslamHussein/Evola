package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.getOrNull
import evola.shared.local.AppSettings
import evola.shared.local.LocalSettingsRepository
import evola.shared.todayLocalDate
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularySessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * Reword-style swipe queue session: a [VocabularyCard.New] word is swiped left ("already know it")
 * or right ("start learning it"); a [VocabularyCard.Practice] word - still learning, or due for
 * review - is swiped left ("got it"/"memorized it") or right ("missed it"/"keep showing"), or
 * checked via typed input or multiple choice instead of a plain swipe. Exiting mid-card is always
 * safe - the repository durably tracks queue position, so re-entering always resumes exactly where
 * the user left off (except a [VocabSessionSource.Category] session, which is a one-off and always
 * starts fresh).
 *
 * [settings] is kept as a plain second `StateFlow` here (not folded into [VocabularySessionUiState])
 * - it's a genuinely independent reactive subscription (device-wide settings, unrelated lifecycle to
 * the session state machine) that the screen consumes alongside the Orbit-driven session state via
 * the ordinary `collectAsStateWithLifecycle()` path. Drives the session screen's invert-swipe
 * direction and which of the two non-swipe checks (typed/multiple-choice) are offered - see
 * [evola.composeapp.main.SettingsScreen].
 */
class VocabularySessionViewModel(
    private val source: VocabSessionSource,
    private val repository: VocabularyRepository,
    private val settingsRepository: LocalSettingsRepository,
) : ViewModel(), OrbitContainerHost<VocabularySessionUiState, VocabularySessionUiState, Nothing> {

    override val container =
        orbitContainer<VocabularySessionUiState, Nothing>(VocabularySessionUiState.Loading, onCreate = { refresh() })

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Dismisses the first-run swipe tutorial overlay for good - see [evola.composeapp.lessons.SwipeTutorialOverlay]. */
    fun markSwipeTutorialSeen() {
        viewModelScope.launch { settingsRepository.setHasSeenSwipeTutorial(true) }
    }

    private suspend fun Syntax<VocabularySessionUiState, Nothing>.advance(sessionId: String, answer: VocabularyAnswerResult) {
        val next = answer.next
        if (next != null) {
            reduce { VocabularySessionUiState.InProgress(next) }
        } else {
            reduce { state.inProgressOrState { copy(answered = answer) } }
            finishCurrentSession(sessionId)
        }
    }

    private suspend fun Syntax<VocabularySessionUiState, Nothing>.refresh() {
        reduce { VocabularySessionUiState.Loading }
        val result = when (val s = source) {
            is VocabSessionSource.Lesson -> repository.startOrResumeSession(s.lessonId)
            is VocabSessionSource.Category -> repository.startCategorySession(s.goalId, s.category)
            is VocabSessionSource.Mode -> repository.startModeSession(s.goalId, s.mode)
        }
        reduce {
            when (result) {
                is ApiResult.Success -> VocabularySessionUiState.InProgress(result.data)
                is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private suspend fun Syntax<VocabularySessionUiState, Nothing>.finishCurrentSession(sessionId: String) {
        val newState = when (val result = repository.complete(sessionId, todayLocalDate())) {
            is ApiResult.Success -> VocabularySessionUiState.Summary(result.data)
            is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
        }
        reduce { newState }
    }

    private fun withBookmark(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.New -> card.copy(isBookmarked = value)
        is VocabularyCard.Practice -> card.copy(isBookmarked = value)
    }

    private fun withDifficult(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.New -> card.copy(markedDifficult = value)
        is VocabularyCard.Practice -> card.copy(markedDifficult = value)
    }

    fun retry() = intent { refresh() }

    // New card swipe left - "I already know this word". Never graded, advances in one call.
    fun submitAlreadyKnown(sessionId: String, itemId: String) = intent {
        when (val result = repository.submitAlreadyKnown(sessionId, itemId)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> advance(sessionId, result.data)
        }
    }

    // New card swipe right - "Start learning this word". Never graded, advances in one call.
    fun submitStartLearning(sessionId: String, itemId: String) = intent {
        when (val result = repository.submitStartLearning(sessionId, itemId)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> advance(sessionId, result.data)
        }
    }

    // Practice card plain swipe, self-reported. Grades but doesn't advance until Continue/Finish.
    fun submitSelfGrade(sessionId: String, itemId: String, correct: Boolean) = intent {
        when (val result = repository.submitSelfGrade(sessionId, itemId, correct)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> reduce { state.inProgressOrState { copy(answered = result.data, canUndo = true) } }
        }
    }

    // Practice card swipe right on a still-learning word - "Keep showing this word".
    // Never graded, advances in one call (no reveal, nothing to confirm).
    fun submitKeepShowing(sessionId: String, itemId: String) = intent {
        when (val result = repository.submitKeepShowing(sessionId, itemId)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> advance(sessionId, result.data)
        }
    }

    // Multiple-choice check: tapping an option grades it but doesn't advance until Continue/Finish.
    fun submitChoice(sessionId: String, itemId: String, selectedChoice: String) = intent {
        when (val result = repository.submitChoice(sessionId, itemId, selectedChoice)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> reduce { state.inProgressOrState { copy(answered = result.data, canUndo = true) } }
        }
    }

    // Typed check: "Check" grades the typed answer but doesn't advance until Continue/Finish.
    fun submitTyped(sessionId: String, itemId: String, response: String) = intent {
        when (val result = repository.submitTyped(sessionId, itemId, response)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> reduce { state.inProgressOrState { copy(answered = result.data, canUndo = true) } }
        }
    }

    // Advances to the already-fetched next card, or finishes the session if the queue is exhausted.
    fun continueToNext(sessionId: String, next: VocabularySessionState?) = intent {
        if (next != null) reduce { VocabularySessionUiState.InProgress(next) } else finishCurrentSession(sessionId)
    }

    fun finishSession(sessionId: String) = intent { finishCurrentSession(sessionId) }

    fun toggleBookmark(itemId: String, newValue: Boolean) = intent {
        val updated = repository.updateFlags(itemId, isBookmarked = newValue).getOrNull() ?: return@intent
        reduce {
            state.inProgressOrState {
                if (session.card.itemId == itemId) copy(session = session.copy(card = withBookmark(session.card, updated.isBookmarked))) else this
            }
        }
    }

    fun toggleDifficult(itemId: String, newValue: Boolean) = intent {
        val updated = repository.updateFlags(itemId, markedDifficult = newValue).getOrNull() ?: return@intent
        reduce {
            state.inProgressOrState {
                if (session.card.itemId == itemId) copy(session = session.copy(card = withDifficult(session.card, updated.markedDifficult))) else this
            }
        }
    }

    // New card's "AI explain" toggle - fetches (and caches server-side) a short note on the word.
    fun explainWord(itemId: String) = intent {
        reduce { state.inProgressOrState { copy(explainLoading = true) } }
        val note = repository.explainItem(itemId).getOrNull()
        reduce {
            state.inProgressOrState {
                val card = session.card
                if (note != null && card is VocabularyCard.New && card.itemId == itemId) {
                    copy(explainLoading = false, session = session.copy(card = card.copy(aiExplanation = note)))
                } else {
                    copy(explainLoading = false)
                }
            }
        }
    }

    fun startNextSession() = intent { refresh() }

    // Reword's per-card undo - see VocabularyRepository.undoLastGrade's own doc comment. A null
    // result (nothing to undo) is a safe no-op, not an error.
    fun undoLastGrade(sessionId: String) = intent {
        when (val result = repository.undoLastGrade(sessionId)) {
            is ApiResult.Failure -> reduce { VocabularySessionUiState.Error(result.error.toUserMessage()) }
            is ApiResult.Success -> result.data?.let { data -> reduce { VocabularySessionUiState.InProgress(data) } }
        }
    }
}

/** Mirrors FlowMVI's typed `updateState<InProgress, _> { ... }` DSL: applies [block] only when the
 * current state is [VocabularySessionUiState.InProgress], otherwise leaves the state untouched. */
private inline fun VocabularySessionUiState.inProgressOrState(
    block: VocabularySessionUiState.InProgress.() -> VocabularySessionUiState,
): VocabularySessionUiState = if (this is VocabularySessionUiState.InProgress) block() else this
