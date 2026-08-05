package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.materials.Material
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MaterialsListState {
    data object Loading : MaterialsListState
    data class Loaded(val materials: List<Material>) : MaterialsListState
    data class Error(val message: String) : MaterialsListState
}

class MaterialsListViewModel(
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MaterialsListState>(MaterialsListState.Loading)
    val state: StateFlow<MaterialsListState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = MaterialsListState.Loading
            _state.value = repository.list().fold(
                onSuccess = { MaterialsListState.Loaded(it) },
                onFailure = { MaterialsListState.Error(it.toUserMessage()) },
            )
        }
    }
}
