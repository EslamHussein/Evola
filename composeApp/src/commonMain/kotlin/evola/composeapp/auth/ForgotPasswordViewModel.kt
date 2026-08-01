package evola.composeapp.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.auth.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitted = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    fun requestReset(email: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                authRepository.requestPasswordReset(email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Always show the generic confirmation regardless (no account-enumeration leak,
                // and a transient network hiccup here shouldn't block the user from retrying).
            } finally {
                _isSubmitting.value = false
                _submitted.value = true
            }
        }
    }
}
