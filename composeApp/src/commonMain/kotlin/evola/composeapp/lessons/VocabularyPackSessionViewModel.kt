package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.vocabulary.VocabularyPack
import evola.shared.vocabulary.VocabularyPackSummary
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularyStageAnswerResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VocabularyPackSessionState {
    data object Loading : VocabularyPackSessionState
    data class InProgress(
        val pack: VocabularyPack,
        val selectedChoice: String? = null,
        val answered: VocabularyStageAnswerResult? = null,
    ) : VocabularyPackSessionState
    data class Summary(val summary: VocabularyPackSummary, val packNumber: Int) : VocabularyPackSessionState
    data object Empty : VocabularyPackSessionState
    data class Error(val message: String) : VocabularyPackSessionState
}

/**
 * Pack/stage vocabulary session (design handoff Phase 7/8): one word at a time, walked through 7
 * fixed stages. Exiting mid-word is always safe - the server durably tracks pack/word/stage
 * position (`VocabularyRepository.startOrResumeSession`), so re-entering always resumes exactly
 * where the user left off, the same guarantee the old flat-session model had.
 */
class VocabularyPackSessionViewModel(
    private val accessToken: String,
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VocabularyPackSessionState>(VocabularyPackSessionState.Loading)
    val state: StateFlow<VocabularyPackSessionState> = _state.asStateFlow()

    private var lastPackNumber = 1

    init {
        refresh()
    }

    fun retry() = refresh()

    /** Stage 0 (Discover) has no input and is never graded - submit and advance in one tap, since
     * the footer never shows a "Check" step for this stage. */
    fun continueFromDiscover() = submit(stageIndex = 0, response = "", autoAdvance = true)

    /** Stage 1 (Recognition): tapping a choice reveals the correct answer client-side (the word's
     * own meaning is already known, no round-trip needed to know what's correct) and submits in
     * the background - the design never blocks on right/wrong here, only on the explicit Continue
     * tap that follows. */
    fun selectRecognitionChoice(choice: String) {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        _state.value = current.copy(selectedChoice = choice)
        submit(stageIndex = 1, response = choice, autoAdvance = false)
    }

    /** Stages 2-6: typed/free-text "Check" - grades the answer but doesn't advance until the user
     * taps Continue/Finish pack (matches the design's Check-then-reveal-then-Continue footer). */
    fun checkAnswer(response: String) {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        submit(stageIndex = current.pack.stageIndex, response = response, autoAdvance = false)
    }

    /** Advances to the already-fetched next stage/word, or finishes the pack if the last word's
     * last stage is done. */
    fun continueToNext() {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        val next = current.answered?.next ?: return
        _state.value = if (next.readyToComplete) current.copy(pack = next, answered = current.answered) else VocabularyPackSessionState.InProgress(next)
    }

    fun finishPack() {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        val packId = current.pack.packId
        val packNumber = current.pack.packNumber
        viewModelScope.launch {
            try {
                val summary = repository.complete(accessToken, packId)
                _state.value = if (summary != null) {
                    VocabularyPackSessionState.Summary(summary, packNumber)
                } else {
                    VocabularyPackSessionState.Error("Couldn't finish this pack.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VocabularyPackSessionState.Error(e.message ?: "Something went wrong.")
            }
        }
    }

    fun toggleBookmark() {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        val word = current.pack.word
        viewModelScope.launch {
            val updated = runCatching { repository.updateFlags(accessToken, word.itemId, isBookmarked = !word.isBookmarked) }.getOrNull()
                ?: return@launch
            val latest = state.value as? VocabularyPackSessionState.InProgress ?: return@launch
            if (latest.pack.word.itemId == word.itemId) {
                _state.value = latest.copy(pack = latest.pack.copy(word = latest.pack.word.copy(isBookmarked = updated.isBookmarked)))
            }
        }
    }

    fun toggleDifficult() {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        val word = current.pack.word
        viewModelScope.launch {
            val updated = runCatching { repository.updateFlags(accessToken, word.itemId, markedDifficult = !word.markedDifficult) }.getOrNull()
                ?: return@launch
            val latest = state.value as? VocabularyPackSessionState.InProgress ?: return@launch
            if (latest.pack.word.itemId == word.itemId) {
                _state.value = latest.copy(pack = latest.pack.copy(word = latest.pack.word.copy(markedDifficult = updated.markedDifficult)))
            }
        }
    }

    fun startNextPack() = refresh()

    private fun refresh() {
        viewModelScope.launch {
            _state.value = VocabularyPackSessionState.Loading
            try {
                val pack = repository.startOrResumeSession(accessToken, lessonId)
                _state.value = if (pack != null) {
                    lastPackNumber = pack.packNumber
                    VocabularyPackSessionState.InProgress(pack)
                } else {
                    VocabularyPackSessionState.Empty
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VocabularyPackSessionState.Error(e.message ?: "Something went wrong.")
            }
        }
    }

    private fun submit(stageIndex: Int, response: String, autoAdvance: Boolean) {
        val current = state.value as? VocabularyPackSessionState.InProgress ?: return
        val itemId = current.pack.word.itemId
        viewModelScope.launch {
            try {
                val result = repository.answer(accessToken, current.pack.packId, itemId, stageIndex, response)
                if (result == null) {
                    _state.value = VocabularyPackSessionState.Error("Couldn't submit your answer.")
                    return@launch
                }
                _state.value = if (autoAdvance) {
                    val next = result.next
                    when {
                        next == null -> VocabularyPackSessionState.Error("Couldn't load the next step.")
                        next.readyToComplete -> current.copy(pack = next, answered = result)
                        else -> VocabularyPackSessionState.InProgress(next)
                    }
                } else {
                    current.copy(answered = result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VocabularyPackSessionState.Error(e.message ?: "Something went wrong.")
            }
        }
    }
}
