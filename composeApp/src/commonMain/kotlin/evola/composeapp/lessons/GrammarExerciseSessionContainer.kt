package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.grammar.GrammarRepository
import evola.shared.todayLocalDate
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/**
 * Grammar's exercise session is a flat multiple-choice/fill-in-blank list (unlike Vocabulary's
 * pack/7-stage model) - modeled directly on the pre-redesign VocabularySessionViewModel. After
 * every answer, the remaining queue is *re-fetched from the server* (`startOrResumeSession` again)
 * rather than managed locally, so a resumed session always reflects the server's own
 * already-answered bookkeeping.
 */
class GrammarExerciseSessionContainer(
    private val topicId: String,
    private val repository: GrammarRepository,
) : Container<GrammarExerciseSessionState, GrammarExerciseSessionIntent, Nothing> {

    private var sessionId: String? = null
    private var answeredCount = 0
    private var correctCount = 0

    private suspend fun PipelineContext<GrammarExerciseSessionState, GrammarExerciseSessionIntent, Nothing>.refresh() {
        updateState { GrammarExerciseSessionState.Loading }
        loadNext()
    }

    private suspend fun PipelineContext<GrammarExerciseSessionState, GrammarExerciseSessionIntent, Nothing>.loadNext() {
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
                updateState { newState }
            }
            is ApiResult.Failure -> updateState { GrammarExerciseSessionState.Error(result.error.toUserMessage()) }
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

    override val store = store(initial = GrammarExerciseSessionState.Loading) {
        configure { name = "GrammarExerciseSessionStore" }
        init { refresh() }
        reduce { intent ->
            when (intent) {
                GrammarExerciseSessionIntent.Retry -> refresh()
                is GrammarExerciseSessionIntent.SubmitAnswer -> {
                    val sid = sessionId ?: return@reduce
                    when (val result = repository.answer(sid, intent.exerciseId, intent.response, intent.correct)) {
                        is ApiResult.Success -> {
                            answeredCount++
                            if (intent.correct) correctCount++
                            loadNext()
                        }
                        is ApiResult.Failure -> updateState { GrammarExerciseSessionState.Error(result.error.toUserMessage()) }
                    }
                }
            }
        }
    }
}
