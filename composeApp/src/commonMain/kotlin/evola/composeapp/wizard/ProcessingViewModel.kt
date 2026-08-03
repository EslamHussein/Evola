package evola.composeapp.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProcessingState {
    data object InProgress : ProcessingState
    data class Done(val materialId: String) : ProcessingState
    data class Error(val message: String) : ProcessingState
}

private val TERMINAL_STATUSES = setOf(MaterialStatus.READY, MaterialStatus.FAILED, MaterialStatus.UNSUPPORTED_CONTENT)
private const val POLL_INTERVAL_MS = 3000L

/** The processing/loading screen the design handoff's own README admits is a gap - polls the
 * just-created material the same way [evola.composeapp.materials.MaterialDetailViewModel] polls
 * an existing one, then hands off to Resource Details once segmentation reaches a terminal state
 * (regardless of outcome - Resource Details already knows how to render FAILED/UNSUPPORTED_CONTENT). */
class ProcessingViewModel(
    private val accessToken: String,
    private val materialId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.InProgress)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    init {
        pollUntilTerminal()
    }

    private fun pollUntilTerminal() {
        viewModelScope.launch {
            while (true) {
                val detail = try {
                    repository.get(accessToken, materialId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = ProcessingState.Error(e.message ?: "Failed to check progress.")
                    return@launch
                }
                if (detail.material.status in TERMINAL_STATUSES) {
                    _state.value = ProcessingState.Done(materialId)
                    return@launch
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
