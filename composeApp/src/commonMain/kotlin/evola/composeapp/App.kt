package evola.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import evola.composeapp.onboarding.OnboardingApp
import evola.composeapp.onboarding.OnboardingViewModel
import evola.shared.auth.HttpAuthRepository

@Composable
fun App() {
    val viewModel = remember { OnboardingViewModel(HttpAuthRepository(baseUrl = defaultServerBaseUrl())) }
    OnboardingApp(viewModel)
}
