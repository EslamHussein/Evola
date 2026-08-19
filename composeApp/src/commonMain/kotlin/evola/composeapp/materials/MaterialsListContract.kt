package evola.composeapp.materials

import evola.shared.materials.Material

sealed interface MaterialsListState {
    data object Loading : MaterialsListState
    data class Loaded(val materials: List<Material>) : MaterialsListState
    data class Error(val message: String) : MaterialsListState
}
