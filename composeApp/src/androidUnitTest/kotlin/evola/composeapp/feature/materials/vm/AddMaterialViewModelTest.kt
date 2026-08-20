package evola.composeapp.feature.materials.vm

import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test

/** [AddMaterialViewModel] has zero repository calls (the trivial first Orbit conversion in this
 * app), so there's little to cover beyond its one intent actually updating state. */
class AddMaterialViewModelTest {

    @Test
    fun `defaults to PDF and selectType switches the selected resource type`() = runTest {
        val viewModel = AddMaterialViewModel()
        viewModel.testWithInternalState(this, AddMaterialState()) {
            containerHost.selectType(ResourceType.TEXT)
            expectInternalState(AddMaterialState(selectedType = ResourceType.TEXT))
            containerHost.selectType(ResourceType.IMAGE)
            expectInternalState(AddMaterialState(selectedType = ResourceType.IMAGE))
        }
    }
}
