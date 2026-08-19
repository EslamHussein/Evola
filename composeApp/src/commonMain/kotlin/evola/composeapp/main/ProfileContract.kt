package evola.composeapp.main

import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.language.NativeLanguage
import kotlin.random.Random
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

/** [GoalUpdated]'s outcome is delivered as part of state rather than as an `MVIAction` -
 * `subscribeConsume`/action-consumption isn't visible from `commonMain` in FlowMVI 3.1.0 (only
 * exists in the platform-specific artifacts, not the KMP common API surface, confirmed by a real
 * "Unresolved reference" build failure) - so every one-shot outcome in this app's FlowMVI migration
 * goes through a state field instead. [id] makes each event distinct even if two edits produce an
 * equal [Goal], so a screen's `LaunchedEffect(state.goalUpdated?.id)` fires exactly once per update. */
data class GoalUpdateEvent(val goal: Goal, val id: Long = Random.nextLong())

data class ProgressResetEvent(val success: Boolean, val id: Long = Random.nextLong())

data class ProfileState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val goalUpdated: GoalUpdateEvent? = null,
    val progressReset: ProgressResetEvent? = null,
    val unlockedBadgeIds: Set<String> = emptySet(),
    val progress: GoalProgress? = null,
) : MVIState

sealed interface ProfileIntent : MVIIntent {
    data class UpdateGoal(
        val goalId: String,
        val goalText: String,
        val title: String?,
        val nativeLanguage: NativeLanguage,
    ) : ProfileIntent

    /** Reword's Menu "Reset all progress" - confirmed by the screen before dispatch. */
    data object ResetAllProgress : ProfileIntent
}
