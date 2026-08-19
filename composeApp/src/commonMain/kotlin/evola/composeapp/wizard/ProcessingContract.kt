package evola.composeapp.wizard

import evola.shared.materials.MaterialDetail
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface ProcessingState : MVIState {
    data object Loading : ProcessingState
    data class InProgress(val detail: MaterialDetail) : ProcessingState
    data class Done(val materialId: String) : ProcessingState
    data class Error(val message: String) : ProcessingState
}

sealed interface ProcessingIntent : MVIIntent
