package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.EvolaLog
import evola.shared.grammar.GrammarRepository
import evola.shared.todayLocalDate
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

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
) : ViewModel(), OrbitContainerHost<GrammarExerciseSessionState, GrammarExerciseSessionState, Nothing> {

    private var sessionId: String? = null
    private var answeredCount = 0
    private var correctCount = 0

    override val container =
        orbitContainer<GrammarExerciseSessionState, Nothing>(GrammarExerciseSessionState.Loading, onCreate = { refresh() })

    private suspend fun Syntax<GrammarExerciseSessionState, Nothing>.refresh() {
        reduce { GrammarExerciseSessionState.Loading }
        loadNext()
    }

    private suspend fun Syntax<GrammarExerciseSessionState, Nothing>.loadNext() {
        when (val result = repository.startOrResumeSession(topicId)) {
            is ApiResult.Success -> {
                val session = result.data
                sessionId = session.sessionId
                val unanswered = session.exercises.firstOrNull { !it.answered }
                val newState = when {
                    unanswered != null -> GrammarExerciseSessionState.InProgress(unanswered, answeredCount)
                    session.exercises.isEmpty() -> GrammarExerciseSessionState.Empty
                    else -> completeSession()
                }
                reduce { newState }
            }
            is ApiResult.Failure -> reduce { GrammarExerciseSessionState.Error(result.error.toUserMessage()) }
        }
    }

    private suspend fun completeSession(): GrammarExerciseSessionState {
        val sid = sessionId ?: return GrammarExerciseSessionState.Error("Session missing.")
        return when (val result = repository.complete(sid, todayLocalDate())) {
            is ApiResult.Success -> GrammarExerciseSessionState.Summary(result.data.exercisesCompleted, result.data.accuracy)
            // Completion is a bookkeeping call; if it fails, still show a summary from what we
            // counted locally rather than blocking the user on a finished session.
            is ApiResult.Failure -> {
                EvolaLog.d("grammar-session", "complete($sid) failed, falling back to local tally: ${result.error}")
                val fallbackAccuracy = if (answeredCount > 0) (correctCount.toDouble() / answeredCount) * 100.0 else 0.0
                GrammarExerciseSessionState.Summary(answeredCount, fallbackAccuracy)
            }
        }
    }

    fun retry() = intent { refresh() }

    fun submitAnswer(exerciseId: String, response: String, correct: Boolean) = intent {
        val sid = sessionId ?: return@intent
        when (val result = repository.answer(sid, exerciseId, response, correct)) {
            is ApiResult.Success -> {
                answeredCount++
                if (correct) correctCount++
                loadNext()
            }
            is ApiResult.Failure -> reduce { GrammarExerciseSessionState.Error(result.error.toUserMessage()) }
        }
    }
}
