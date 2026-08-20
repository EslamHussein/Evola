package evola.composeapp.feature.learning.vm

import evola.shared.feature.learning.domain.GrammarTopic

sealed interface GrammarTopicListState {
    data object Loading : GrammarTopicListState
    data class Loaded(val topics: List<GrammarTopic>) : GrammarTopicListState
    data class Error(val message: String) : GrammarTopicListState
}
