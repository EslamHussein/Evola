package evola.composeapp.lessons

import evola.shared.grammar.GrammarTopic

sealed interface GrammarTopicListState {
    data object Loading : GrammarTopicListState
    data class Loaded(val topics: List<GrammarTopic>) : GrammarTopicListState
    data class Error(val message: String) : GrammarTopicListState
}
