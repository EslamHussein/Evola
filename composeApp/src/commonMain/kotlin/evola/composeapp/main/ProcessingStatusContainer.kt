package evola.composeapp.main

import evola.shared.core.EvolaLog
import evola.shared.core.getOrNull
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.asyncInit
import pro.respawn.flowmvi.plugins.reduce

private const val POLL_INTERVAL_MS = 3000L

/**
 * App-wide (not screen-scoped) view of materials currently mid-processing, so the persistent
 * bottom sheet in [MainScreen] can show live progress no matter which tab the user is on. Same
 * poll-the-local-DB-every-3s shape as [evola.composeapp.materials.MaterialsListContainer] and
 * [evola.composeapp.wizard.ProcessingContainer] - `list()` is a local SQLDelight query, not a
 * network call, so continuous polling here is cheap.
 */
class ProcessingStatusContainer(
    private val materialsRepository: MaterialsRepository,
) : Container<ProcessingStatusState, ProcessingStatusIntent, Nothing> {

    override val store = store(initial = ProcessingStatusState()) {
        configure { name = "ProcessingStatusStore" }
        asyncInit {
            while (true) {
                try {
                    val materials = materialsRepository.list().getOrNull().orEmpty()
                    updateState { ProcessingStatusState(materials.filter { it.status == MaterialStatus.PROCESSING }) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A crashed poll tick must not kill this app-wide loop for the rest of the
                    // process lifetime - log and retry on the next tick instead.
                    EvolaLog.d("processing-status", "poll tick failed: $e")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        reduce { }
    }
}
