package evola.composeapp.onboarding

import evola.shared.goals.Goal

data class GoalSetupState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface GoalSetupSideEffect {
    data class GoalCreated(val goal: Goal) : GoalSetupSideEffect
}
