package evola.composeapp.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.goals.CreateGoalResult
import evola.shared.goals.Goal
import evola.shared.goals.GoalsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Goal Setup per 01_PRODUCT_SPEC.md §1.4 - goal_text 3-200 chars required, title optional. */
class GoalSetupViewModel(
    private val goalsRepository: GoalsRepository,
    private val accessToken: String,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun createGoal(goalText: String, title: String?, onSuccess: (Goal) -> Unit) {
        val trimmedText = goalText.trim()
        if (trimmedText.length < 3) {
            _errorMessage.value = "Tell us a bit more about your goal (at least 3 characters)."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                when (val result = goalsRepository.createGoal(accessToken, trimmedText, title?.trim()?.ifBlank { null })) {
                    is CreateGoalResult.Success -> onSuccess(result.goal)
                    CreateGoalResult.ActiveGoalExists -> onSuccess(
                        // Shouldn't normally happen from this screen, but if it does the user
                        // already has a goal - just continue rather than blocking them here.
                        goalsRepository.getActiveGoal(accessToken) ?: run {
                            _errorMessage.value = "You already have an active goal."
                            return@launch
                        },
                    )
                    is CreateGoalResult.ValidationError -> _errorMessage.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Something went wrong. Please try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
