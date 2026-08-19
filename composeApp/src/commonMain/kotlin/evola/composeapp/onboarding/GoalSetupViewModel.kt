package evola.composeapp.onboarding

import evola.shared.goals.GoalsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class GoalSetupViewModel(goalsRepository: GoalsRepository) :
    StoreViewModel<GoalSetupState, GoalSetupIntent, Nothing>(GoalSetupContainer(goalsRepository))
