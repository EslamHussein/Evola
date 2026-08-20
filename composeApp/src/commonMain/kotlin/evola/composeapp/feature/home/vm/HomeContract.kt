package evola.composeapp.feature.home.vm

import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.Lesson

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
