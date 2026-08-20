package evola.composeapp.core.navigation

import evola.shared.language.NativeLanguage
import kotlinx.serialization.Serializable

/** Navigation 3 route model for the app-level splash/onboarding/main flow, same rationale as
 * [evola.composeapp.main.MaterialsRoute]. There is deliberately no back-navigation wired anywhere
 * in this flow (matches the pre-migration behavior - no `BackHandler` existed here either), so every
 * transition below is a full stack replace (`backStack.clear(); backStack.add(...)`), not a push. */
@Serializable
sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object VocabDataImport : AppRoute
    @Serializable data object OnboardingWelcome : AppRoute
    @Serializable data object NativeLanguageSetup : AppRoute
    @Serializable data class GoalSetup(val nativeLanguage: NativeLanguage) : AppRoute
    @Serializable data object DailyGoalSetup : AppRoute
    @Serializable data object CategoryPicker : AppRoute
    @Serializable data object Main : AppRoute
}
