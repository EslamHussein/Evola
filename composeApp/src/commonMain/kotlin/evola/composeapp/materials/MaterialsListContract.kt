package evola.composeapp.materials

import evola.shared.materials.Material
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface MaterialsListState : MVIState {
    data object Loading : MaterialsListState
    data class Loaded(val materials: List<Material>) : MaterialsListState
    data class Error(val message: String) : MaterialsListState
}

sealed interface MaterialsListIntent : MVIIntent {
    data object Refresh : MaterialsListIntent
    data class Delete(val materialId: String) : MaterialsListIntent
}
