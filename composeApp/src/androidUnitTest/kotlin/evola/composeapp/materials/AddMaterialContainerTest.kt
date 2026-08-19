package evola.composeapp.materials

import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait
import kotlin.test.Test
import kotlin.test.assertEquals

/** [AddMaterialContainer] has zero repository calls (the trivial first FlowMVI conversion in this
 * app), so there's little to cover beyond its one intent actually updating state. */
class AddMaterialContainerTest {

    @Test
    fun `defaults to PDF and SelectType switches the selected resource type`() = runTest {
        AddMaterialContainer().store.subscribeAndTest {
            assertEquals(ResourceType.PDF, states.value.selectedType)
            AddMaterialIntent.SelectType(ResourceType.TEXT) resultsIn {
                wait()
                assertEquals(ResourceType.TEXT, states.value.selectedType)
            }
            AddMaterialIntent.SelectType(ResourceType.IMAGE) resultsIn {
                wait()
                assertEquals(ResourceType.IMAGE, states.value.selectedType)
            }
        }
    }
}
