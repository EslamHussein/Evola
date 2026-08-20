package evola.composeapp.feature.vocabulary.vm

import evola.shared.feature.vocabulary.domain.SessionMode
import evola.shared.feature.vocabulary.domain.VocabularyAnswerResult
import evola.shared.feature.vocabulary.domain.VocabularySessionState
import evola.shared.feature.vocabulary.domain.VocabularySessionSummary
import evola.shared.feature.vocabulary.domain.WordCategory

sealed interface VocabularySessionUiState {
    data object Loading : VocabularySessionUiState
    data class InProgress(
        val session: VocabularySessionState,
        val answered: VocabularyAnswerResult? = null,
        val explainLoading: Boolean = false,
        /** True right after a graded [VocabularyCard.Practice] answer (self-grade/typed/choice) -
         * see [evola.shared.feature.vocabulary.domain.VocabularyRepository.undoLastGrade]'s own doc comment for
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
 * [evola.shared.feature.vocabulary.domain.VocabularyRepository.startOrResumeSession]) or a Home red/yellow/green
 * category cutting across every lesson in the goal (one-off, per
 * [evola.shared.feature.vocabulary.domain.VocabularyRepository.startCategorySession]). */
sealed interface VocabSessionSource {
    data class Lesson(val lessonId: String) : VocabSessionSource
    data class Category(val goalId: String, val category: WordCategory) : VocabSessionSource

    /** Reword's Home "Learn new words"/"Review words"/"Mixed mode" rows - see
     * [evola.shared.feature.vocabulary.domain.VocabularyRepository.startModeSession]. */
    data class Mode(val goalId: String, val mode: SessionMode) : VocabSessionSource
}
