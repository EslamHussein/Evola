package evola.composeapp.main

import evola.shared.feature.onboarding.domain.Goal
import evola.shared.feature.onboarding.domain.GoalProgress

data class ProfileState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val unlockedBadgeIds: Set<String> = emptySet(),
    val progress: GoalProgress? = null,
)

sealed interface ProfileSideEffect {
    data class GoalUpdated(val goal: Goal) : ProfileSideEffect
    data class ProgressReset(val success: Boolean) : ProfileSideEffect
}
