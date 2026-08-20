package evola.composeapp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import evola.shared.goals.Goal

/** Koin-scoped context for the app-level onboarding/main flow - same rationale as
 * [evola.composeapp.main.MaterialsNavContext].
 *
 * - [resolvedTarget] is a real [MutableState] (not a plain `var`, unlike [resolvedGoal] below) since
 *   [AppRoute.Splash] and [AppRoute.VocabDataImport] both need to *observe* it - it starts `null` and
 *   is set once, asynchronously, by [App]'s own `LaunchedEffect` (the active-goal lookup that runs in
 *   parallel with the splash animation) - a plain field write wouldn't recompose those routes when it
 *   resolves. [App] creates the `mutableStateOf` and assigns it here once; routes read `.value`.
 * - [resolvedGoal] is the [Goal] to open [AppRoute.Main] with - either an existing active goal found
 *   by that same lookup, or the one just created by [AppRoute.GoalSetup]. Read-once by
 *   [AppRoute.CategoryPicker]/[AppRoute.Main] after being set, so (unlike [resolvedTarget]) a plain
 *   field is fine here - nothing needs to observe it changing.
 */
class AppNavContext {
    lateinit var backStack: SnapshotStateList<AppRoute>
    lateinit var resolvedTarget: MutableState<AppRoute?>
    var resolvedGoal: Goal? = null
}
