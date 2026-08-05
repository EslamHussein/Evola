package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.Job
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

private val TERMINAL_STATUSES = setOf(MaterialStatus.READY, MaterialStatus.FAILED, MaterialStatus.UNSUPPORTED_CONTENT)
private const val POLL_INTERVAL_MS = 3000L

/** Polls while the material's status isn't terminal yet, so the UI shows real lesson-generation
 * progress. Tracks [pollJob] so [retry] restarts polling instead of racing a second loop against
 * the one already running from [init]. */
class MaterialDetailViewModel(
    private val materialId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MaterialDetailState>(MaterialDetailState.Loading)
    val state: StateFlow<MaterialDetailState> = _state.asStateFlow()
    private var pollJob: Job? = null

    init {
        pollUntilTerminal()
    }

    fun retry() {
        pollJob?.cancel()
        viewModelScope.launch {
            // A reprocess failure is surfaced on the next poll tick if the material is still FAILED.
            repository.reprocess(materialId)
            pollUntilTerminal()
        }
    }

    private fun pollUntilTerminal() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                when (val result = repository.get(materialId)) {
                    is ApiResult.Failure -> {
                        _state.value = MaterialDetailState.Error(result.error.toUserMessage())
                        return@launch
                    }
                    is ApiResult.Success -> {
                        _state.value = MaterialDetailState.Loaded(result.data)
                        if (result.data.material.status in TERMINAL_STATUSES) return@launch
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }
}
