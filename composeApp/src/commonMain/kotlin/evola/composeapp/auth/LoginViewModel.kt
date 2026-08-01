package evola.composeapp.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.auth.AuthRepository
import evola.shared.auth.AuthResult
import evola.shared.auth.AuthTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun login(email: String, password: String, onSuccess: (AuthTokens) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                when (val result = authRepository.login(email, password)) {
                    is AuthResult.Success -> onSuccess(result.tokens)
                    AuthResult.InvalidCredentials -> _errorMessage.value = "Email or password is incorrect."
                    is AuthResult.AccountLocked ->
                        _errorMessage.value = "Too many attempts. Try again in ${result.minutesRemaining} minutes."
                    AuthResult.EmailTaken, is AuthResult.ValidationError ->
                        _errorMessage.value = "Something went wrong. Please try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Login failed. Please try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
