package evola.composeapp.wizard

import evola.shared.materials.MaterialDetail

sealed interface ProcessingState {
    data object Loading : ProcessingState
    data class InProgress(val detail: MaterialDetail) : ProcessingState
    data class Done(val materialId: String) : ProcessingState
    data class Error(val message: String) : ProcessingState
}
