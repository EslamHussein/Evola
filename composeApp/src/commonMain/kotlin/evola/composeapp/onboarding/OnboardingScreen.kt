package evola.composeapp.onboarding

sealed interface OnboardingScreen {
    data object Welcome : OnboardingScreen
    data class SignUp(val isSubmitting: Boolean = false) : OnboardingScreen
    data class Error(val message: String) : OnboardingScreen
    data class Success(val userId: String) : OnboardingScreen
}
