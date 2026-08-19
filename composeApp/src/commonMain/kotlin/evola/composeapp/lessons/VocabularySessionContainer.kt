package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.getOrNull
import evola.shared.todayLocalDate
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.dsl.updateState
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/**
 * Reword-style swipe queue session: a [VocabularyCard.New] word is swiped left ("already know it")
 * or right ("start learning it"); a [VocabularyCard.Practice] word - still learning, or due for
 * review - is swiped left ("got it"/"memorized it") or right ("missed it"/"keep showing"), or
 * checked via typed input or multiple choice instead of a plain swipe. Exiting mid-card is always
 * safe - the repository durably tracks queue position, so re-entering always resumes exactly where
 * the user left off (except a [VocabSessionSource.Category] session, which is a one-off and always
 * starts fresh).
 */
class VocabularySessionContainer(
    private val source: VocabSessionSource,
    private val repository: VocabularyRepository,
) : Container<VocabularySessionUiState, VocabularySessionIntent, Nothing> {

    private suspend fun PipelineContext<VocabularySessionUiState, VocabularySessionIntent, Nothing>.advance(
        sessionId: String,
        answer: VocabularyAnswerResult,
    ) {
        val next = answer.next
        if (next != null) {
            updateState { VocabularySessionUiState.InProgress(next) }
        } else {
            updateState<VocabularySessionUiState.InProgress, _> { copy(answered = answer) }
            finishSession(sessionId)
        }
    }

    private suspend fun PipelineContext<VocabularySessionUiState, VocabularySessionIntent, Nothing>.refresh() {
        updateState { VocabularySessionUiState.Loading }
        val result = when (val s = source) {
            is VocabSessionSource.Lesson -> repository.startOrResumeSession(s.lessonId)
            is VocabSessionSource.Category -> repository.startCategorySession(s.goalId, s.category)
            is VocabSessionSource.Mode -> repository.startModeSession(s.goalId, s.mode)
        }
        updateState {
            when (result) {
                is ApiResult.Success -> VocabularySessionUiState.InProgress(result.data)
                is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private suspend fun PipelineContext<VocabularySessionUiState, VocabularySessionIntent, Nothing>.finishSession(sessionId: String) {
        val newState = when (val result = repository.complete(sessionId, todayLocalDate())) {
            is ApiResult.Success -> VocabularySessionUiState.Summary(result.data)
            is ApiResult.Failure -> VocabularySessionUiState.Error(result.error.toUserMessage())
        }
        updateState { newState }
    }

    private fun withBookmark(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.New -> card.copy(isBookmarked = value)
        is VocabularyCard.Practice -> card.copy(isBookmarked = value)
    }

    private fun withDifficult(card: VocabularyCard, value: Boolean) = when (card) {
        is VocabularyCard.New -> card.copy(markedDifficult = value)
        is VocabularyCard.Practice -> card.copy(markedDifficult = value)
    }

    override val store = store(initial = VocabularySessionUiState.Loading) {
        configure { name = "VocabularySessionStore" }
        init { refresh() }
        reduce { intent ->
            when (intent) {
                VocabularySessionIntent.Retry -> refresh()

                // New card swipe left - "I already know this word". Never graded, advances in one call.
                is VocabularySessionIntent.SubmitAlreadyKnown -> {
                    when (val result = repository.submitAlreadyKnown(intent.sessionId, intent.itemId)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> advance(intent.sessionId, result.data)
                    }
                }

                // New card swipe right - "Start learning this word". Never graded, advances in one call.
                is VocabularySessionIntent.SubmitStartLearning -> {
                    when (val result = repository.submitStartLearning(intent.sessionId, intent.itemId)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> advance(intent.sessionId, result.data)
                    }
                }

                // Practice card plain swipe, self-reported. Grades but doesn't advance until Continue/Finish.
                is VocabularySessionIntent.SubmitSelfGrade -> {
                    when (val result = repository.submitSelfGrade(intent.sessionId, intent.itemId, intent.correct)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> updateState<VocabularySessionUiState.InProgress, _> { copy(answered = result.data, canUndo = true) }
                    }
                }

                // Practice card swipe right on a still-learning word - "Keep showing this word".
                // Never graded, advances in one call (no reveal, nothing to confirm).
                is VocabularySessionIntent.SubmitKeepShowing -> {
                    when (val result = repository.submitKeepShowing(intent.sessionId, intent.itemId)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> advance(intent.sessionId, result.data)
                    }
                }

                // Multiple-choice check: tapping an option grades it but doesn't advance until Continue/Finish.
                is VocabularySessionIntent.SubmitChoice -> {
                    when (val result = repository.submitChoice(intent.sessionId, intent.itemId, intent.selectedChoice)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> updateState<VocabularySessionUiState.InProgress, _> { copy(answered = result.data, canUndo = true) }
                    }
                }

                // Typed check: "Check" grades the typed answer but doesn't advance until Continue/Finish.
                is VocabularySessionIntent.SubmitTyped -> {
                    when (val result = repository.submitTyped(intent.sessionId, intent.itemId, intent.response)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> updateState<VocabularySessionUiState.InProgress, _> { copy(answered = result.data, canUndo = true) }
                    }
                }

                // Advances to the already-fetched next card, or finishes the session if the queue is exhausted.
                is VocabularySessionIntent.ContinueToNext -> {
                    val next = intent.next
                    if (next != null) updateState { VocabularySessionUiState.InProgress(next) } else finishSession(intent.sessionId)
                }

                is VocabularySessionIntent.FinishSession -> finishSession(intent.sessionId)

                is VocabularySessionIntent.ToggleBookmark -> {
                    val updated = repository.updateFlags(intent.itemId, isBookmarked = intent.newValue).getOrNull() ?: return@reduce
                    updateState<VocabularySessionUiState.InProgress, _> {
                        if (session.card.itemId == intent.itemId) {
                            copy(session = session.copy(card = withBookmark(session.card, updated.isBookmarked)))
                        } else {
                            this
                        }
                    }
                }

                is VocabularySessionIntent.ToggleDifficult -> {
                    val updated = repository.updateFlags(intent.itemId, markedDifficult = intent.newValue).getOrNull() ?: return@reduce
                    updateState<VocabularySessionUiState.InProgress, _> {
                        if (session.card.itemId == intent.itemId) {
                            copy(session = session.copy(card = withDifficult(session.card, updated.markedDifficult)))
                        } else {
                            this
                        }
                    }
                }

                // New card's "AI explain" toggle - fetches (and caches server-side) a short note on
                // the word. Unlike the pre-FlowMVI version, this no longer skips a redundant call
                // while already loading/already has a note (that guard needed a synchronous state
                // read this Container can no longer do) - a deliberate, minor simplification, not a
                // functional regression: worst case a double-tap fires one extra harmless repository call.
                is VocabularySessionIntent.ExplainWord -> {
                    updateState<VocabularySessionUiState.InProgress, _> { copy(explainLoading = true) }
                    val note = repository.explainItem(intent.itemId).getOrNull()
                    updateState<VocabularySessionUiState.InProgress, _> {
                        val card = session.card
                        if (note != null && card is VocabularyCard.New && card.itemId == intent.itemId) {
                            copy(explainLoading = false, session = session.copy(card = card.copy(aiExplanation = note)))
                        } else {
                            copy(explainLoading = false)
                        }
                    }
                }

                VocabularySessionIntent.StartNextSession -> refresh()

                // Reword's per-card undo - see VocabularyRepository.undoLastGrade's own doc comment.
                // A null result (nothing to undo) is a safe no-op, not an error.
                is VocabularySessionIntent.UndoLastGrade -> {
                    when (val result = repository.undoLastGrade(intent.sessionId)) {
                        is ApiResult.Failure -> updateState { VocabularySessionUiState.Error(result.error.toUserMessage()) }
                        is ApiResult.Success -> result.data?.let { updateState { VocabularySessionUiState.InProgress(it) } }
                    }
                }
            }
        }
    }
}
