package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class AddMaterialViewModel : ViewModel(), OrbitContainerHost<AddMaterialState, AddMaterialState, Nothing> {

    override val container = orbitContainer<AddMaterialState, Nothing>(AddMaterialState())

    fun selectType(type: ResourceType) = intent {
        reduce { state.copy(selectedType = type) }
    }
}
