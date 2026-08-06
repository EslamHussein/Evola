package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.getOrNull
import evola.shared.todayLocalDate
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularySessionState
import evola.shared.vocabulary.VocabularySessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VocabularySessionUiState {
    data object Loading : VocabularySessionUiState
    data class InProgress(
        val session: VocabularySessionState,
        val answered: VocabularyAnswerResult? = null,
    ) : VocabularySessionUiState
    data class Summary(val summary: VocabularySessionSummary) : VocabularySessionUiState
    data object Empty : VocabularySessionUiState
    data class Error(val message: String) : VocabularySessionUiState
}

/**
 * Lingvist-style flat SRS queue session, extended with a per-word difficulty ladder for new words:
 * Intro -> Recognition (MC) -> WordBank (tap) -> Hint (typed, pre-filled) -> Blind (typed). A
 * due-for-review word skips straight to Blind. Exiting mid-card is always safe - the repository
 * durably tracks queue position, so re-entering always resumes exactly where the user left off.
 */
class VocabularySessionViewModel(
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VocabularySessionUiState>(VocabularySessionUiState.Loading)
    val state: StateFlow<VocabularySessionUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun retry() = refresh()

    /** Intro card has no input and is never graded - "Got it" submits and advances in one tap. */
    fun submitIntro() {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val itemId = current.session.card.itemId
        viewModelScope.launch {
            when (val result = repository.submitIntro(current.session.sessionId, itemId)) {
                is ApiResult.Failure -> _state.value = VocabularySessionUiState.Error(result.error.toUserMessage())
                is ApiResult.Success -> advance(current, result.data)
            }
        }
    }

    /** Recognition/WordBank: tapping an option grades it but doesn't advance until the user taps
     * Continue/Finish (matches the design's Check-then-reveal-then-Continue footer). */
    fun submitChoice(selectedChoice: String) {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val itemId = current.session.card.itemId
        viewModelScope.launch {
            when (val result = repository.submitChoice(current.session.sessionId, itemId, selectedChoice)) {
                is ApiResult.Failure -> _state.value = VocabularySessionUiState.Error(result.error.toUserMessage())
                is ApiResult.Success -> _state.value = current.copy(answered = result.data)
            }
        }
    }

    /** Hint/Blind: "Check" grades the typed answer but doesn't advance until the user taps
     * Continue/Finish. */
    fun submitTyped(response: String) {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val itemId = current.session.card.itemId
        viewModelScope.launch {
            when (val result = repository.submitTyped(current.session.sessionId, itemId, response)) {
                is ApiResult.Failure -> _state.value = VocabularySessionUiState.Error(result.error.toUserMessage())
                is ApiResult.Success -> _state.value = current.copy(answered = result.data)
            }
        }
    }

    /** Advances to the already-fetched next card, or finishes the session if the queue is exhausted. */
    fun continueToNext() {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val next = current.answered?.next
        if (next != null) _state.value = VocabularySessionUiState.InProgress(next) else finishSession()
    }

    fun finishSession() {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val sessionId = current.session.sessionId
        viewModelScope.launch {
            _state.value = when (val result = repository.complete(sessionId, todayLocalDate())) {
                is ApiResult.Success -> VocabularySessionUiState.Summary(result.data)
                is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
            }
        }
    }

    fun toggleBookmark() {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val card = current.session.card
        viewModelScope.launch {
            val updated = repository.updateFlags(card.itemId, isBookmarked = !currentIsBookmarked(card)).getOrNull()
                ?: return@launch
            val latest = state.value as? VocabularySessionUiState.InProgress ?: return@launch
            if (latest.session.card.itemId == card.itemId) {
                _state.value = latest.copy(session = latest.session.copy(card = withBookmark(latest.session.card, updated.isBookmarked)))
            }
        }
    }

    fun toggleDifficult() {
        val current = state.value as? VocabularySessionUiState.InProgress ?: return
        val card = current.session.card
        viewModelScope.launch {
            val updated = repository.updateFlags(card.itemId, markedDifficult = !currentIsDifficult(card)).getOrNull()
                ?: return@launch
            val latest = state.value as? VocabularySessionUiState.InProgress ?: return@launch
            if (latest.session.card.itemId == card.itemId) {
                _state.value = latest.copy(session = latest.session.copy(card = withDifficult(latest.session.card, updated.markedDifficult)))
            }
        }
    }

    fun startNextSession() = refresh()

    private fun advance(current: VocabularySessionUiState.InProgress, answer: VocabularyAnswerResult) {
        val next = answer.next
        if (next != null) {
            _state.value = VocabularySessionUiState.InProgress(next)
        } else {
            _state.value = current.copy(answered = answer)
            finishSession()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = VocabularySessionUiState.Loading
            _state.value = when (val result = repository.startOrResumeSession(lessonId)) {
                is ApiResult.Success -> VocabularySessionUiState.InProgress(result.data)
                is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private fun currentIsBookmarked(card: VocabularyCard) = when (card) {
        is VocabularyCard.Intro -> card.isBookmarked
        is VocabularyCard.Recognition -> card.isBookmarked
        is VocabularyCard.WordBank -> card.isBookmarked
        is VocabularyCard.Hint -> card.isBookmarked
        is VocabularyCard.Blind -> card.isBookmarked
    }

    private fun currentIsDifficult(card: VocabularyCard) = when (card) {
        is VocabularyCard.Intro -> card.markedDifficult
        is VocabularyCard.Recognition -> card.markedDifficult
        is VocabularyCard.WordBank -> card.markedDifficult
        is VocabularyCard.Hint -> card.markedDifficult
        is VocabularyCard.Blind -> card.markedDifficult
    }

    private fun withBookmark(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.Intro -> card.copy(isBookmarked = value)
        is VocabularyCard.Recognition -> card.copy(isBookmarked = value)
        is VocabularyCard.WordBank -> card.copy(isBookmarked = value)
        is VocabularyCard.Hint -> card.copy(isBookmarked = value)
        is VocabularyCard.Blind -> card.copy(isBookmarked = value)
    }

    private fun withDifficult(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.Intro -> card.copy(markedDifficult = value)
        is VocabularyCard.Recognition -> card.copy(markedDifficult = value)
        is VocabularyCard.WordBank -> card.copy(markedDifficult = value)
        is VocabularyCard.Hint -> card.copy(markedDifficult = value)
        is VocabularyCard.Blind -> card.copy(markedDifficult = value)
    }
}
