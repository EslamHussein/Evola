package evola.composeapp.feature.onboarding.vm

import evola.shared.feature.onboarding.domain.Goal

data class GoalSetupState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface GoalSetupSideEffect {
    data class GoalCreated(val goal: Goal) : GoalSetupSideEffect
}
