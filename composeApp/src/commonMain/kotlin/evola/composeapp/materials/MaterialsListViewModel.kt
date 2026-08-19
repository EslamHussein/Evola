package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.EvolaLog
import evola.shared.core.getOrNull
import evola.shared.materials.MATERIAL_POLL_INTERVAL_MS
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

class MaterialsListViewModel(
    private val materialsRepository: MaterialsRepository,
) : ViewModel(), OrbitContainerHost<MaterialsListState, MaterialsListState, Nothing> {

    override val container = orbitContainer<MaterialsListState, Nothing>(MaterialsListState.Loading, onCreate = { refreshInternal() })

    private var pollJob: Job? = null

    private suspend fun Syntax<MaterialsListState, Nothing>.loadOnce() {
        when (val materials = materialsRepository.list()) {
            is ApiResult.Failure -> reduce { MaterialsListState.Error(materials.error.toUserMessage()) }
            is ApiResult.Success -> {
                reduce { MaterialsListState.Loaded(materials.data) }
                // While any material is still PROCESSING, keep re-fetching so its live progress
                // (lesson counts) shows up here too without a manual pull-to-refresh - same
                // MATERIAL_POLL_INTERVAL_MS and "update in place, don't flash back to Loading" shape
                // as MaterialDetailViewModel. Stops itself once nothing is processing anymore.
                if (materials.data.any { it.status == MaterialStatus.PROCESSING }) {
                    startPolling()
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = intent {
            while (true) {
                delay(MATERIAL_POLL_INTERVAL_MS)
                val next = materialsRepository.list().getOrNull() ?: return@intent
                reduce { MaterialsListState.Loaded(next) }
                if (next.none { it.status == MaterialStatus.PROCESSING }) return@intent
            }
        }
    }

    private suspend fun Syntax<MaterialsListState, Nothing>.refreshInternal() {
        pollJob?.cancel()
        reduce { MaterialsListState.Loading }
        loadOnce()
    }

    fun refresh() = intent { refreshInternal() }

    fun delete(materialId: String) = intent {
        val result = materialsRepository.deleteMaterial(materialId)
        if (result is ApiResult.Failure) EvolaLog.d("materials-list", "deleteMaterial failed: ${result.error}")
        refreshInternal()
    }
}
