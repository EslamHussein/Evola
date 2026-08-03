package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VocabularyListState {
    data object Loading : VocabularyListState
    data class Loaded(val items: List<VocabularyItem>) : VocabularyListState
}

class VocabularyListViewModel(
    private val accessToken: String,
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<VocabularyListState>(VocabularyListState.Loading)
    val state: StateFlow<VocabularyListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = VocabularyListState.Loaded(repository.listVocabulary(accessToken, lessonId))
        }
    }
}
