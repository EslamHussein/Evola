package evola.composeapp.onboarding

import evola.shared.core.getOrNull
import evola.shared.goals.CreateGoalResult
import evola.shared.goals.GoalsRepository
import kotlinx.coroutines.CancellationException
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce

/** Goal Setup per 01_PRODUCT_SPEC.md §1.4 - goal_text 3-200 chars required, title optional. */
class GoalSetupContainer(private val goalsRepository: GoalsRepository) : Container<GoalSetupState, GoalSetupIntent, Nothing> {

    override val store = store(initial = GoalSetupState()) {
        configure { name = "GoalSetupStore" }
        reduce { intent ->
            when (intent) {
                is GoalSetupIntent.CreateGoal -> {
                    // Length validation lives solely in GoalsRepository.createGoal (returns
                    // CreateGoalResult.ValidationError) - a duplicate check here previously
                    // disagreed with the repository's real 3-200 bound and message.
                    val trimmedText = intent.goalText.trim()
                    updateState { copy(isSubmitting = true, errorMessage = null) }
                    try {
                        when (
                            val result = goalsRepository.createGoal(trimmedText, intent.title?.trim()?.ifBlank { null }, intent.nativeLanguage)
                        ) {
                            is CreateGoalResult.Success -> updateState { copy(goalCreated = GoalCreateEvent(result.goal)) }
                            CreateGoalResult.ActiveGoalExists -> {
                                // Shouldn't normally happen from this screen, but if it does the user
                                // already has a goal - just continue rather than blocking them here.
                                val active = goalsRepository.getActiveGoal().getOrNull()
                                if (active != null) {
                                    updateState { copy(goalCreated = GoalCreateEvent(active)) }
                                } else {
                                    updateState { copy(errorMessage = "You already have an active goal.") }
                                }
                            }
                            is CreateGoalResult.ValidationError -> updateState { copy(errorMessage = result.message) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        updateState { copy(errorMessage = e.message ?: "Something went wrong. Please try again.") }
                    } finally {
                        updateState { copy(isSubmitting = false) }
                    }
                }
            }
        }
    }
}
