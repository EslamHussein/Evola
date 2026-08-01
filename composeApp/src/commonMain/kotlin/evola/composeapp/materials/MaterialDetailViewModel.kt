package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MaterialDetailState {
    data object Loading : MaterialDetailState
    data class Loaded(val detail: MaterialDetail) : MaterialDetailState
    data class Error(val message: String) : MaterialDetailState
}

private val TERMINAL_STATUSES = setOf(MaterialStatus.ANALYZED, MaterialStatus.FAILED, MaterialStatus.NEEDS_REVIEW)
private const val POLL_INTERVAL_MS = 3000L

/** Polls while the material's status isn't terminal yet, so the UI shows real extraction progress. */
class MaterialDetailViewModel(
    private val materialId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MaterialDetailState>(MaterialDetailState.Loading)
    val state: StateFlow<MaterialDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val detail = try {
                    repository.get(materialId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = MaterialDetailState.Error(e.message ?: "Failed to load material.")
                    return@launch
                }
                _state.value = MaterialDetailState.Loaded(detail)
                if (detail.material.status in TERMINAL_STATUSES) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
