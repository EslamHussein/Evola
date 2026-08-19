package evola.composeapp.main

import evola.shared.goals.GoalsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class HomeViewModel(goalId: String, repository: GoalsRepository) :
    StoreViewModel<HomeState, HomeIntent, Nothing>(HomeContainer(goalId, repository))
