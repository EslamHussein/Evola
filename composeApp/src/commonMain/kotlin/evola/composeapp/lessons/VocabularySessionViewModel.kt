package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularySessionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VocabularySessionState {
    data object Loading : VocabularySessionState
    data class InProgress(val currentItem: VocabularySessionItem, val answeredCount: Int) : VocabularySessionState
    data class Summary(val itemsCount: Int, val accuracy: Double) : VocabularySessionState
    /** hasLessonVocabulary=false is "this lesson has zero words of its own" (§1.8 edge case);
     * true-but-empty is "nothing due, nothing new" (a different edge case, different messaging). */
    data class Empty(val hasLessonVocabulary: Boolean) : VocabularySessionState
    data class Error(val message: String) : VocabularySessionState
}

/**
 * Consolidates Session Start / Drill / Summary (06_SCREENS_REFERENCE.md screens #13-15) into one
 * state machine rather than three navigation destinations - simpler wiring for what's fundamentally
 * one linear flow. After every answer, the remaining queue is *re-fetched from the server*
 * (`startOrResumeSession` again) rather than managed locally - the server is the only place that
 * knows the correct position of a just-resurfaced item, so this is what actually implements
 * "wrong twice -> resurfaced later, not immediately" correctly on the client.
 */
class VocabularySessionViewModel(
    private val accessToken: String,
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VocabularySessionState>(VocabularySessionState.Loading)
    val state: StateFlow<VocabularySessionState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var answeredCount = 0
    private var correctCount = 0

    init {
        refresh()
    }

    fun retry() = refresh()

    fun submitAnswer(response: String, correct: Boolean) {
        val current = (state.value as? VocabularySessionState.InProgress)?.currentItem ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            try {
                val result = repository.answer(accessToken, sid, current.itemId, response, correct)
                if (result == null) {
                    _state.value = VocabularySessionState.Error("Couldn't submit your answer.")
                    return@launch
                }
                answeredCount++
                if (correct) correctCount++
                loadNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = VocabularySessionState.Error(e.message ?: "Something went wrong.")
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = VocabularySessionState.Loading
            loadNext()
        }
    }

    private suspend fun loadNext() {
        try {
            val session = repository.startOrResumeSession(accessToken, lessonId)
            if (session == null) {
                _state.value = VocabularySessionState.Error("Couldn't load this lesson's vocabulary.")
                return
            }
            sessionId = session.sessionId
            _state.value = when {
                session.items.isNotEmpty() -> VocabularySessionState.InProgress(session.items.first(), answeredCount)
                answeredCount > 0 -> completeSession()
                else -> VocabularySessionState.Empty(session.hasLessonVocabulary)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = VocabularySessionState.Error(e.message ?: "Something went wrong.")
        }
    }

    private suspend fun completeSession(): VocabularySessionState {
        val sid = sessionId ?: return VocabularySessionState.Error("Session missing.")
        val summary = repository.complete(accessToken, sid)
        return if (summary != null) {
            VocabularySessionState.Summary(summary.itemsCount, summary.accuracy)
        } else {
            val fallbackAccuracy = if (answeredCount > 0) (correctCount.toDouble() / answeredCount) * 100.0 else 0.0
            VocabularySessionState.Summary(answeredCount, fallbackAccuracy)
        }
    }
}
