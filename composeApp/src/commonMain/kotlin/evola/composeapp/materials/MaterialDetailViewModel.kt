package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import evola.composeapp.core.common.toUserMessage
import evola.shared.core.common.ApiResult
import evola.shared.core.analytics.EvolaLog
import evola.shared.materials.MATERIAL_POLL_INTERVAL_MS
import evola.shared.materials.MATERIAL_TERMINAL_STATUSES
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Polls while the material's status isn't terminal yet, so the UI shows real lesson-generation
 * progress. Tracks the poll job manually (cancel-then-replace) so [retry]/[deleteLesson] restart
 * polling instead of racing a second loop against the one already running from `onCreate` -
 * mirrors FlowMVI's `manageJobs()`-keyed job replacement this app used everywhere else. */
class MaterialDetailViewModel(
    private val materialId: String,
    private val repository: MaterialsRepository,
) : ViewModel(), OrbitContainerHost<MaterialDetailState, MaterialDetailState, Nothing> {

    override val container = orbitContainer<MaterialDetailState, Nothing>(MaterialDetailState.Loading, onCreate = { startPolling() })

    private var pollJob: Job? = null

    private fun startPolling(): Job {
        pollJob?.cancel()
        val job = intent {
            while (true) {
                when (val result = repository.get(materialId)) {
                    is ApiResult.Failure -> {
                        reduce { MaterialDetailState.Error(result.error.toUserMessage()) }
                        return@intent
                    }
                    is ApiResult.Success -> {
                        reduce { MaterialDetailState.Loaded(result.data) }
                        if (result.data.material.status in MATERIAL_TERMINAL_STATUSES) return@intent
                        delay(MATERIAL_POLL_INTERVAL_MS)
                    }
                }
            }
        }
        pollJob = job
        return job
    }

    fun retry() = intent {
        // A reprocess failure (e.g. the material isn't actually FAILED anymore) leaves status
        // unchanged, so the next poll tick shows the same state as before the tap - logged here
        // since that's otherwise invisible.
        val result = repository.reprocess(materialId)
        if (result is ApiResult.Failure) EvolaLog.d("material-detail", "reprocess failed: ${result.error}")
        startPolling()
    }

    fun deleteLesson(lessonId: String) = intent {
        val result = repository.deleteLesson(lessonId)
        if (result is ApiResult.Failure) EvolaLog.d("material-detail", "deleteLesson failed: ${result.error}")
        startPolling()
    }
}
