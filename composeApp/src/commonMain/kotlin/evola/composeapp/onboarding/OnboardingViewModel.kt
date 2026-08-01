package evola.composeapp.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.auth.AuthRepository
import evola.shared.auth.SignUpRequest
import evola.shared.auth.SignUpResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _screen = MutableStateFlow<OnboardingScreen>(OnboardingScreen.Welcome)
    val screen: StateFlow<OnboardingScreen> = _screen.asStateFlow()

    fun startSignUp() {
        _screen.value = OnboardingScreen.SignUp()
    }

    fun signUp(email: String, password: String) {
        _screen.value = OnboardingScreen.SignUp(isSubmitting = true)
        viewModelScope.launch {
            _screen.value = try {
                when (val result = authRepository.signUp(SignUpRequest(email, password))) {
                    is SignUpResult.Success -> OnboardingScreen.Success(result.userId)
                    SignUpResult.EmailAlreadyTaken -> OnboardingScreen.Error("That email is already registered.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnboardingScreen.Error(e.message ?: "Sign up failed. Please try again.")
            }
        }
    }

    fun retry() {
        _screen.value = OnboardingScreen.SignUp()
    }
}
