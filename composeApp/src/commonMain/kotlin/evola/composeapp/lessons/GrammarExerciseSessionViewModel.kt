package evola.composeapp.lessons

import evola.shared.grammar.GrammarRepository
import pro.respawn.flowmvi.android.StoreViewModel

class GrammarExerciseSessionViewModel(topicId: String, repository: GrammarRepository) :
    StoreViewModel<GrammarExerciseSessionState, GrammarExerciseSessionIntent, Nothing>(
        GrammarExerciseSessionContainer(topicId, repository),
    )
