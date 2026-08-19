package evola.composeapp.wizard

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.materials.MATERIAL_POLL_INTERVAL_MS
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.delay
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.asyncInit
import pro.respawn.flowmvi.plugins.reduce

private val TERMINAL_STATUSES = setOf(MaterialStatus.READY, MaterialStatus.FAILED, MaterialStatus.UNSUPPORTED_CONTENT)

/** The processing/loading screen the design handoff's own README admits is a gap - polls the
 * just-created material the same way [evola.composeapp.materials.MaterialDetailContainer] polls
 * an existing one, then hands off to Resource Details once segmentation reaches a terminal state
 * (regardless of outcome - Resource Details already knows how to render FAILED/UNSUPPORTED_CONTENT).
 * Carries the full [evola.shared.materials.MaterialDetail] on every tick (not just a terminal/not-terminal
 * flag) so [evola.composeapp.wizard.ProcessingScreen] can render real per-lesson progress. */
class ProcessingContainer(
    private val materialId: String,
    private val repository: MaterialsRepository,
) : Container<ProcessingState, ProcessingIntent, Nothing> {

    override val store = store(initial = ProcessingState.Loading) {
        configure { name = "ProcessingStore" }
        asyncInit {
            while (true) {
                when (val result = repository.get(materialId)) {
                    is ApiResult.Failure -> {
                        updateState { ProcessingState.Error(result.error.toUserMessage()) }
                        return@asyncInit
                    }
                    is ApiResult.Success -> {
                        if (result.data.material.status in TERMINAL_STATUSES) {
                            updateState { ProcessingState.Done(materialId) }
                            return@asyncInit
                        }
                        updateState { ProcessingState.InProgress(result.data) }
                    }
                }
                delay(MATERIAL_POLL_INTERVAL_MS)
            }
        }
        reduce { }
    }
}
