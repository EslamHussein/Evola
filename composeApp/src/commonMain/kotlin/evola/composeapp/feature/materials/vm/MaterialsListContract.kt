package evola.composeapp.feature.materials.vm

import evola.shared.feature.materials.domain.Material

sealed interface MaterialsListState {
    data object Loading : MaterialsListState
    data class Loaded(val materials: List<Material>) : MaterialsListState
    data class Error(val message: String) : MaterialsListState
}
