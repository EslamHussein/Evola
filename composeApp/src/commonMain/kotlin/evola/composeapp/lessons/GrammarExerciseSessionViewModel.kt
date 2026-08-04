package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.grammar.GrammarExercise
import evola.shared.todayLocalDate
import evola.shared.grammar.GrammarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GrammarExerciseSessionState {
    data object Loading : GrammarExerciseSessionState
    data class InProgress(val currentExercise: GrammarExercise, val answeredCount: Int) : GrammarExerciseSessionState
    data class Summary(val exercisesCompleted: Int, val accuracy: Double) : GrammarExerciseSessionState
    /** This topic ended up with 0 valid exercises (spec: a topic can survive validation with
     * fewer than 3, or even 0, valid exercises - the explanation is still shown on the topic list,
     * this is not an error). */
    data object Empty : GrammarExerciseSessionState
    data class Error(val message: String) : GrammarExerciseSessionState
}

/**
 * Grammar's exercise session is a flat multiple-choice/fill-in-blank list (unlike Vocabulary's
 * pack/7-stage model) - modeled directly on the pre-redesign VocabularySessionViewModel. After
 * every answer, the remaining queue is *re-fetched from the server* (`startOrResumeSession` again)
 * rather than managed locally, so a resumed session always reflects the server's own
 * already-answered bookkeeping.
 */
class GrammarExerciseSessionViewModel(
    private val accessToken: String,
    private val topicId: String,
    private val repository: GrammarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GrammarExerciseSessionState>(GrammarExerciseSessionState.Loading)
    val state: StateFlow<GrammarExerciseSessionState> = _state.asStateFlow()

    private var sessionId: String? = null
    private var answeredCount = 0
    private var correctCount = 0

    init {
        refresh()
    }

    fun retry() = refresh()

    fun submitAnswer(response: String, correct: Boolean) {
        val current = (state.value as? GrammarExerciseSessionState.InProgress)?.currentExercise ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            try {
                val result = repository.answer(accessToken, sid, current.exerciseId, response, correct)
                if (result == null) {
                    _state.value = GrammarExerciseSessionState.Error("Couldn't submit your answer.")
                    return@launch
                }
                answeredCount++
                if (correct) correctCount++
                loadNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = GrammarExerciseSessionState.Error(e.message ?: "Something went wrong.")
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = GrammarExerciseSessionState.Loading
            loadNext()
        }
    }

    private suspend fun loadNext() {
        try {
            val session = repository.startOrResumeSession(accessToken, topicId)
            if (session == null) {
                _state.value = GrammarExerciseSessionState.Error("Couldn't load this topic's exercises.")
                return
            }
            sessionId = session.sessionId
            val unanswered = session.exercises.firstOrNull { !it.answered }
            _state.value = when {
                unanswered != null -> GrammarExerciseSessionState.InProgress(unanswered, answeredCount)
                session.exercises.isEmpty() -> GrammarExerciseSessionState.Empty
                else -> completeSession()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = GrammarExerciseSessionState.Error(e.message ?: "Something went wrong.")
        }
    }

    private suspend fun completeSession(): GrammarExerciseSessionState {
        val sid = sessionId ?: return GrammarExerciseSessionState.Error("Session missing.")
        val summary = repository.complete(accessToken, sid, todayLocalDate())
        return if (summary != null) {
            GrammarExerciseSessionState.Summary(summary.exercisesCompleted, summary.accuracy)
        } else {
            val fallbackAccuracy = if (answeredCount > 0) (correctCount.toDouble() / answeredCount) * 100.0 else 0.0
            GrammarExerciseSessionState.Summary(answeredCount, fallbackAccuracy)
        }
    }
}
