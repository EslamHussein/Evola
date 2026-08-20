package evola.composeapp.feature.materials.vm

import evola.shared.feature.materials.domain.MaterialDetail

sealed interface MaterialDetailState {
    data object Loading : MaterialDetailState
    data class Loaded(val detail: MaterialDetail) : MaterialDetailState
    data class Error(val message: String) : MaterialDetailState
}
