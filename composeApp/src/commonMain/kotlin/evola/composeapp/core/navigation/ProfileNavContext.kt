package evola.composeapp.core.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import evola.composeapp.feature.profile.ui.ProfileRoute
import evola.shared.feature.onboarding.domain.Goal

/** Koin-scoped context for the Profile tab's navigation - same rationale as [MaterialsNavContext].
 * [goal] and [onGoalUpdated] are kept in sync with [MainScreen]'s own `goal` state directly (not via
 * a `LaunchedEffect`, since [ProfileRoute.Main] reads [goal] immediately on first composition and a
 * `LaunchedEffect` would leave it one frame stale). */
class ProfileNavContext {
    lateinit var backStack: SnapshotStateList<ProfileRoute>
    lateinit var goal: Goal
    var onGoalUpdated: (Goal) -> Unit = {}
}
