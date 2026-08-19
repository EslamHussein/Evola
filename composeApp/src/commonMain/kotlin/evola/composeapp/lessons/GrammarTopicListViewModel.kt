package evola.composeapp.lessons

import evola.shared.grammar.GrammarRepository
import pro.respawn.flowmvi.android.StoreViewModel

class GrammarTopicListViewModel(lessonId: String, repository: GrammarRepository) :
    StoreViewModel<GrammarTopicListState, GrammarTopicListIntent, Nothing>(GrammarTopicListContainer(lessonId, repository))
