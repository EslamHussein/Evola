package evola.composeapp.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.goals.Goal
import evola.shared.goals.GoalsRepository
import evola.shared.goals.UpdateGoalResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Goal editing from Profile per 01_PRODUCT_SPEC.md §1.4 - editable later without repeating onboarding. */
class ProfileViewModel(
    private val goalsRepository: GoalsRepository,
    private val accessToken: String,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateGoal(goalId: String, goalText: String, title: String?, onSuccess: (Goal) -> Unit) {
        val trimmedText = goalText.trim()
        if (trimmedText.length < 3) {
            _errorMessage.value = "Tell us a bit more about your goal (at least 3 characters)."
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                when (val result = goalsRepository.updateGoal(accessToken, goalId, trimmedText, title?.trim()?.ifBlank { null })) {
                    is UpdateGoalResult.Success -> onSuccess(result.goal)
                    UpdateGoalResult.NotFound -> _errorMessage.value = "This goal could not be found."
                    is UpdateGoalResult.ValidationError -> _errorMessage.value = result.message
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
