package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VocabularyListState {
    data object Loading : VocabularyListState
    data class Loaded(val items: List<VocabularyItem>) : VocabularyListState
    data class Error(val message: String) : VocabularyListState
}

class VocabularyListViewModel(
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VocabularyListState>(VocabularyListState.Loading)
    val state: StateFlow<VocabularyListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.listVocabulary(lessonId).fold(
                onSuccess = { VocabularyListState.Loaded(it) },
                onFailure = { VocabularyListState.Error(it.toUserMessage()) },
            )
        }
    }

    fun updateItem(itemId: String, term: String, meaning: String, meaningAr: String?) {
        viewModelScope.launch {
            repository.updateItem(itemId, term, meaning, meaningAr).fold(
                onSuccess = { updated ->
                    val current = _state.value
                    if (current is VocabularyListState.Loaded) {
                        _state.value = VocabularyListState.Loaded(
                            current.items.map { if (it.itemId == updated.itemId) updated else it },
                        )
                    }
                },
                onFailure = {},
            )
        }
    }
}
