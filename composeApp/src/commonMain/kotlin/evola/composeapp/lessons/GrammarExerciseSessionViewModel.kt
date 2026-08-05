package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.grammar.GrammarExercise
import evola.shared.grammar.GrammarRepository
import evola.shared.todayLocalDate
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
            when (val result = repository.answer(sid, current.exerciseId, response, correct)) {
                is ApiResult.Success -> {
                    answeredCount++
                    if (correct) correctCount++
                    loadNext()
                }
                is ApiResult.Failure -> _state.value = GrammarExerciseSessionState.Error(result.error.toUserMessage())
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
        when (val result = repository.startOrResumeSession(topicId)) {
            is ApiResult.Success -> {
                val session = result.data
                sessionId = session.sessionId
                val unanswered = session.exercises.firstOrNull { !it.answered }
                _state.value = when {
                    unanswered != null -> GrammarExerciseSessionState.InProgress(unanswered, answeredCount)
                    session.exercises.isEmpty() -> GrammarExerciseSessionState.Empty
                    else -> completeSession()
                }
            }
            is ApiResult.Failure -> _state.value = GrammarExerciseSessionState.Error(result.error.toUserMessage())
        }
    }

    private suspend fun completeSession(): GrammarExerciseSessionState {
        val sid = sessionId ?: return GrammarExerciseSessionState.Error("Session missing.")
        return when (val result = repository.complete(sid, todayLocalDate())) {
            is ApiResult.Success -> GrammarExerciseSessionState.Summary(result.data.exercisesCompleted, result.data.accuracy)
            // Completion is a bookkeeping call; if it fails, still show a summary from what we
            // counted locally rather than blocking the user on a finished session.
            is ApiResult.Failure -> {
                val fallbackAccuracy = if (answeredCount > 0) (correctCount.toDouble() / answeredCount) * 100.0 else 0.0
                GrammarExerciseSessionState.Summary(answeredCount, fallbackAccuracy)
            }
        }
    }
}
