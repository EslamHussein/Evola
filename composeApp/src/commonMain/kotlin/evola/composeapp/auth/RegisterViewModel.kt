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

class RegisterViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun register(fullName: String, email: String, password: String, onSuccess: (AuthTokens) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                when (val result = authRepository.register(fullName, email, password)) {
                    is AuthResult.Success -> onSuccess(result.tokens)
                    AuthResult.EmailTaken -> _errorMessage.value = "An account with this email already exists."
                    is AuthResult.ValidationError -> _errorMessage.value = result.message
                    AuthResult.InvalidCredentials, is AuthResult.AccountLocked ->
                        _errorMessage.value = "Something went wrong. Please try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Registration failed. Please try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
