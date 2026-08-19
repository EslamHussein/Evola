package evola.composeapp.materials

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.EvolaLog
import evola.shared.core.getOrNull
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.manageJobs
import pro.respawn.flowmvi.plugins.reduce

private const val POLL_INTERVAL_MS = 3000L
private const val POLL_JOB_KEY = "poll"

class MaterialsListContainer(
    private val materialsRepository: MaterialsRepository,
) : Container<MaterialsListState, MaterialsListIntent, Nothing> {

    override val store = store(initial = MaterialsListState.Loading) {
        configure { name = "MaterialsListStore" }
        val pollJobs = manageJobs<String, Nothing, MaterialsListIntent, MaterialsListState>()

        suspend fun PipelineContext<MaterialsListState, MaterialsListIntent, Nothing>.loadOnce() {
            when (val materials = materialsRepository.list()) {
                is ApiResult.Failure -> updateState { MaterialsListState.Error(materials.error.toUserMessage()) }
                is ApiResult.Success -> {
                    updateState { MaterialsListState.Loaded(materials.data) }
                    // While any material is still PROCESSING, keep re-fetching so its live progress
                    // (lesson counts) shows up here too without a manual pull-to-refresh - same
                    // POLL_INTERVAL_MS and "update in place, don't flash back to Loading" shape as
                    // MaterialDetailContainer. Stops itself once nothing is processing anymore.
                    if (materials.data.any { it.status == MaterialStatus.PROCESSING }) {
                        pollJobs.putOrReplace(
                            POLL_JOB_KEY,
                            launch {
                                while (true) {
                                    delay(POLL_INTERVAL_MS)
                                    val next = materialsRepository.list().getOrNull() ?: return@launch
                                    updateState { MaterialsListState.Loaded(next) }
                                    if (next.none { it.status == MaterialStatus.PROCESSING }) return@launch
                                }
                            },
                        )
                    }
                }
            }
        }

        suspend fun PipelineContext<MaterialsListState, MaterialsListIntent, Nothing>.refresh() {
            pollJobs.cancelAll()
            updateState { MaterialsListState.Loading }
            loadOnce()
        }

        init { refresh() }
        reduce { intent ->
            when (intent) {
                MaterialsListIntent.Refresh -> refresh()
                is MaterialsListIntent.Delete -> {
                    val result = materialsRepository.deleteMaterial(intent.materialId)
                    if (result is ApiResult.Failure) EvolaLog.d("materials-list", "deleteMaterial failed: ${result.error}")
                    refresh()
                }
            }
        }
    }
}
