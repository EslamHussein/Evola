package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.Material
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
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
    private val userId: String,
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
            _state.value = try {
                MaterialsListState.Loaded(repository.list(userId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MaterialsListState.Error(e.message ?: "Failed to load materials.")
            }
        }
    }
}
