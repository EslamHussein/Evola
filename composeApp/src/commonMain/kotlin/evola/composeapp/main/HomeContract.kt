package evola.composeapp.main

import evola.shared.goals.GoalProgress
import evola.shared.goals.Lesson

sealed interface HomeState {
    data object Loading : HomeState

    /** [currentLesson] is resolved by cross-referencing [GoalProgress.currentLessonId] against the
     * fetched lesson list; null when every lesson is complete (or there are none). [hasLessons]
     * distinguishes the encouraging "upload something" empty state from a real dashboard. */
    data class Loaded(
        val progress: GoalProgress,
        val currentLesson: Lesson?,
        val hasLessons: Boolean,
    ) : HomeState

    data class Error(val message: String) : HomeState
}
