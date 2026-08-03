package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
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
    private val accessToken: String,
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
            try {
                repository.reprocess(accessToken, materialId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surfaced on the next poll tick if the material is still FAILED.
            }
            pollUntilTerminal()
        }
    }

    private fun pollUntilTerminal() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val detail = try {
                    repository.get(accessToken, materialId)
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
