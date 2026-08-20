package evola.composeapp.feature.onboarding.vm

import androidx.lifecycle.ViewModel
import evola.shared.core.common.getOrNull
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.feature.onboarding.domain.GoalsRepository
import evola.shared.language.NativeLanguage
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Goal Setup per 01_PRODUCT_SPEC.md §1.4 - goal_text 3-200 chars required, title optional. */
class GoalSetupViewModel(private val goalsRepository: GoalsRepository) :
    ViewModel(), OrbitContainerHost<GoalSetupState, GoalSetupState, GoalSetupSideEffect> {

    override val container = orbitContainer<GoalSetupState, GoalSetupSideEffect>(GoalSetupState())

    // Length validation lives solely in GoalsRepository.createGoal (returns
    // CreateGoalResult.ValidationError) - a duplicate check here previously disagreed with the
    // repository's real 3-200 bound and message.
    fun createGoal(goalText: String, title: String?, nativeLanguage: NativeLanguage) = intent {
        val trimmedText = goalText.trim()
        reduce { state.copy(isSubmitting = true, errorMessage = null) }
        try {
            when (val result = goalsRepository.createGoal(trimmedText, title?.trim()?.ifBlank { null }, nativeLanguage)) {
                is CreateGoalResult.Success -> postSideEffect(GoalSetupSideEffect.GoalCreated(result.goal))
                CreateGoalResult.ActiveGoalExists -> {
                    // Shouldn't normally happen from this screen, but if it does the user
                    // already has a goal - just continue rather than blocking them here.
                    val active = goalsRepository.getActiveGoal().getOrNull()
                    if (active != null) {
                        postSideEffect(GoalSetupSideEffect.GoalCreated(active))
                    } else {
                        reduce { state.copy(errorMessage = "You already have an active goal.") }
                    }
                }
                is CreateGoalResult.ValidationError -> reduce { state.copy(errorMessage = result.message) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reduce { state.copy(errorMessage = e.message ?: "Something went wrong. Please try again.") }
        } finally {
            reduce { state.copy(isSubmitting = false) }
        }
    }
}
