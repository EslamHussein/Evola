package evola.composeapp.lessons

import evola.shared.vocabulary.SessionMode
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularySessionState
import evola.shared.vocabulary.VocabularySessionSummary
import evola.shared.vocabulary.WordCategory
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface VocabularySessionUiState : MVIState {
    data object Loading : VocabularySessionUiState
    data class InProgress(
        val session: VocabularySessionState,
        val answered: VocabularyAnswerResult? = null,
        val explainLoading: Boolean = false,
        /** True right after a graded [VocabularyCard.Practice] answer (self-grade/typed/choice) -
         * see [evola.shared.vocabulary.VocabularyRepository.undoLastGrade]'s own doc comment for
         * exactly what's covered. Reset to false once undone, once "Continue" advances past it, or
         * once a session resumes fresh (the repository's own undo snapshot is in-memory only, so a
         * stale `true` here would offer an undo that's already unavailable server-side). */
        val canUndo: Boolean = false,
    ) : VocabularySessionUiState
    data class Summary(val summary: VocabularySessionSummary) : VocabularySessionUiState
    data object Empty : VocabularySessionUiState
    data class Error(val message: String) : VocabularySessionUiState
}

/** Where a vocabulary session's words come from - a single lesson (resumable, per
 * [evola.shared.vocabulary.VocabularyRepository.startOrResumeSession]) or a Home red/yellow/green
 * category cutting across every lesson in the goal (one-off, per
 * [evola.shared.vocabulary.VocabularyRepository.startCategorySession]). */
sealed interface VocabSessionSource {
    data class Lesson(val lessonId: String) : VocabSessionSource
    data class Category(val goalId: String, val category: WordCategory) : VocabSessionSource

    /** Reword's Home "Learn new words"/"Review words"/"Mixed mode" rows - see
     * [evola.shared.vocabulary.VocabularyRepository.startModeSession]. */
    data class Mode(val goalId: String, val mode: SessionMode) : VocabSessionSource
}

/** Every intent that operates on the current card carries the [sessionId]/[itemId] (or whatever
 * else it needs) directly, supplied by the screen from the [VocabularySessionUiState.InProgress] it's
 * already rendering - the Container never reads its own current state synchronously to re-derive
 * this data (FlowMVI 3.1.0 hard-blocks the synchronous `states.value` accessor unconditionally, not
 * just behind an opt-in annotation - confirmed by a build failure that `@OptIn(DelicateStoreApi::class)`,
 * both per-declaration and file-level, did not suppress). Patches after a repository call use the
 * typed `updateState<InProgress, _> { ... }` DSL instead, which reads+writes the *current* state
 * safely as part of the same call. */
sealed interface VocabularySessionIntent : MVIIntent {
    data object Retry : VocabularySessionIntent
    data class SubmitAlreadyKnown(val sessionId: String, val itemId: String) : VocabularySessionIntent
    data class SubmitStartLearning(val sessionId: String, val itemId: String) : VocabularySessionIntent
    data class SubmitSelfGrade(val sessionId: String, val itemId: String, val correct: Boolean) : VocabularySessionIntent
    data class SubmitKeepShowing(val sessionId: String, val itemId: String) : VocabularySessionIntent
    data class SubmitChoice(val sessionId: String, val itemId: String, val selectedChoice: String) : VocabularySessionIntent
    data class SubmitTyped(val sessionId: String, val itemId: String, val response: String) : VocabularySessionIntent

    /** [next] is the already-fetched next card from the last graded answer (screen has this as
     * `state.answered?.next`) - null means the queue is exhausted and the session should finish
     * (using [sessionId], also already in scope on the screen as `state.session.sessionId`). */
    data class ContinueToNext(val sessionId: String, val next: VocabularySessionState?) : VocabularySessionIntent
    data class FinishSession(val sessionId: String) : VocabularySessionIntent
    data class ToggleBookmark(val itemId: String, val newValue: Boolean) : VocabularySessionIntent
    data class ToggleDifficult(val itemId: String, val newValue: Boolean) : VocabularySessionIntent
    data class ExplainWord(val itemId: String) : VocabularySessionIntent
    data object StartNextSession : VocabularySessionIntent

    /** Reword's per-card undo button - see [evola.shared.vocabulary.VocabularyRepository.
     * undoLastGrade]'s own doc comment for scope/limits. */
    data class UndoLastGrade(val sessionId: String) : VocabularySessionIntent
}
