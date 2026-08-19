package evola.composeapp.lessons

import evola.shared.grammar.GrammarTopic
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface GrammarTopicListState : MVIState {
    data object Loading : GrammarTopicListState
    data class Loaded(val topics: List<GrammarTopic>) : GrammarTopicListState
    data class Error(val message: String) : GrammarTopicListState
}

sealed interface GrammarTopicListIntent : MVIIntent
