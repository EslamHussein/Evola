package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.grammar.GrammarRepository
import evola.shared.grammar.GrammarTopic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GrammarTopicListState {
    data object Loading : GrammarTopicListState
    data class Loaded(val topics: List<GrammarTopic>) : GrammarTopicListState
    data class Error(val message: String) : GrammarTopicListState
}

class GrammarTopicListViewModel(
    private val lessonId: String,
    private val repository: GrammarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<GrammarTopicListState>(GrammarTopicListState.Loading)
    val state: StateFlow<GrammarTopicListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.listTopics(lessonId).fold(
                onSuccess = { GrammarTopicListState.Loaded(it) },
                onFailure = { GrammarTopicListState.Error(it.toUserMessage()) },
            )
        }
    }
}
