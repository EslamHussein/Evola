package evola.composeapp.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.auth.AuthRepository
import evola.shared.auth.PasswordResetConfirmResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun confirmReset(token: String, newPassword: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                when (val result = authRepository.confirmPasswordReset(token, newPassword)) {
                    PasswordResetConfirmResult.Success -> _success.value = true
                    PasswordResetConfirmResult.TokenInvalidOrExpired ->
                        _errorMessage.value = "This reset link is invalid or has expired. Request a new one."
                    is PasswordResetConfirmResult.ValidationError -> _errorMessage.value = result.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Reset failed. Please try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
