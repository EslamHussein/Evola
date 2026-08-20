package evola.composeapp.feature.materials.vm

import androidx.lifecycle.ViewModel
import evola.composeapp.core.common.toUserMessage
import evola.shared.core.common.ApiResult
import evola.shared.feature.materials.domain.MATERIAL_POLL_INTERVAL_MS
import evola.shared.feature.materials.domain.MATERIAL_TERMINAL_STATUSES
import evola.shared.feature.materials.domain.MaterialsRepository
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** The processing/loading screen the design handoff's own README admits is a gap - polls the
 * just-created material the same way [evola.composeapp.feature.materials.vm.MaterialDetailViewModel] polls
 * an existing one, then hands off to Resource Details once segmentation reaches a terminal state
 * (regardless of outcome - Resource Details already knows how to render FAILED/UNSUPPORTED_CONTENT).
 * Carries the full [evola.shared.feature.materials.domain.MaterialDetail] on every tick (not just a terminal/not-terminal
 * flag) so [evola.composeapp.feature.materials.ui.ProcessingScreen] can render real per-lesson progress. */
class ProcessingViewModel(
    materialId: String,
    repository: MaterialsRepository,
) : ViewModel(), OrbitContainerHost<ProcessingState, ProcessingState, Nothing> {

    override val container = orbitContainer<ProcessingState, Nothing>(ProcessingState.Loading, onCreate = {
        while (true) {
            when (val result = repository.get(materialId)) {
                is ApiResult.Failure -> {
                    reduce { ProcessingState.Error(result.error.toUserMessage()) }
                    return@orbitContainer
                }
                is ApiResult.Success -> {
                    if (result.data.material.status in MATERIAL_TERMINAL_STATUSES) {
                        reduce { ProcessingState.Done(materialId) }
                        return@orbitContainer
                    }
                    reduce { ProcessingState.InProgress(result.data) }
                }
            }
            delay(MATERIAL_POLL_INTERVAL_MS)
        }
    })
}
