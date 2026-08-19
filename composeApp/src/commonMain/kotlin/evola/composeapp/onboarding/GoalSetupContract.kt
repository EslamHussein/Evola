package evola.composeapp.onboarding

import evola.shared.goals.Goal
import evola.shared.language.NativeLanguage
import kotlin.random.Random
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

/** See [evola.composeapp.main.GoalUpdateEvent] - same state-based one-shot-event pattern
 * (`subscribeConsume`/`MVIAction` isn't visible from `commonMain` in FlowMVI 3.1.0). */
data class GoalCreateEvent(val goal: Goal, val id: Long = Random.nextLong())

data class GoalSetupState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val goalCreated: GoalCreateEvent? = null,
) : MVIState

sealed interface GoalSetupIntent : MVIIntent {
    data class CreateGoal(val goalText: String, val title: String?, val nativeLanguage: NativeLanguage) : GoalSetupIntent
}
