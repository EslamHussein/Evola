package evola.composeapp.main

import androidx.lifecycle.ViewModel
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.getOrNull
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

private const val POLL_INTERVAL_MS = 3000L

/**
 * App-wide (not screen-scoped) view of materials currently mid-processing, so the persistent
 * bottom sheet in [MainScreen] can show live progress no matter which tab the user is on. Same
 * poll-the-local-DB-every-3s shape as [evola.composeapp.materials.MaterialsListViewModel] and
 * [evola.composeapp.wizard.ProcessingViewModel] - `list()` is a local SQLDelight query, not a
 * network call, so continuous polling here is cheap.
 */
class ProcessingStatusViewModel(
    private val materialsRepository: MaterialsRepository,
) : ViewModel(), OrbitContainerHost<ProcessingStatusState, ProcessingStatusState, Nothing> {

    override val container = orbitContainer<ProcessingStatusState, Nothing>(ProcessingStatusState(), onCreate = {
        while (true) {
            try {
                val materials = materialsRepository.list().getOrNull().orEmpty()
                reduce { ProcessingStatusState(materials.filter { it.status == MaterialStatus.PROCESSING }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A crashed poll tick must not kill this app-wide loop for the rest of the
                // process lifetime - log and retry on the next tick instead.
                EvolaLog.d("processing-status", "poll tick failed: $e")
            }
            delay(POLL_INTERVAL_MS)
        }
    })
}
