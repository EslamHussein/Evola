package evola.composeapp.materials

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.materials.MaterialStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.manageJobs
import pro.respawn.flowmvi.plugins.reduce

private val TERMINAL_STATUSES = setOf(MaterialStatus.READY, MaterialStatus.FAILED, MaterialStatus.UNSUPPORTED_CONTENT)
private const val POLL_INTERVAL_MS = 3000L
private const val POLL_JOB_KEY = "poll"

/** Polls while the material's status isn't terminal yet, so the UI shows real lesson-generation
 * progress. Tracks the poll job via [manageJobs] so [MaterialDetailIntent.Retry] restarts polling
 * instead of racing a second loop against the one already running from [init]. */
class MaterialDetailContainer(
    private val materialId: String,
    private val repository: evola.shared.materials.MaterialsRepository,
) : Container<MaterialDetailState, MaterialDetailIntent, Nothing> {

    override val store = store(initial = MaterialDetailState.Loading) {
        configure { name = "MaterialDetailStore" }
        val pollJobs = manageJobs<String, Nothing, MaterialDetailIntent, MaterialDetailState>()

        suspend fun PipelineContext<MaterialDetailState, MaterialDetailIntent, Nothing>.pollUntilTerminal() {
            pollJobs.putOrReplace(
                POLL_JOB_KEY,
                launch {
                    while (true) {
                        when (val result = repository.get(materialId)) {
                            is ApiResult.Failure -> {
                                updateState { MaterialDetailState.Error(result.error.toUserMessage()) }
                                return@launch
                            }
                            is ApiResult.Success -> {
                                updateState { MaterialDetailState.Loaded(result.data) }
                                if (result.data.material.status in TERMINAL_STATUSES) return@launch
                                delay(POLL_INTERVAL_MS)
                            }
                        }
                    }
                },
            )
        }

        init { pollUntilTerminal() }
        reduce { intent ->
            when (intent) {
                MaterialDetailIntent.Retry -> {
                    // A reprocess failure is surfaced on the next poll tick if the material is still FAILED.
                    repository.reprocess(materialId)
                    pollUntilTerminal()
                }
                is MaterialDetailIntent.DeleteLesson -> {
                    repository.deleteLesson(intent.lessonId)
                    pollUntilTerminal()
                }
            }
        }
    }
}
