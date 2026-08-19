package evola.composeapp.materials

import evola.shared.materials.MaterialDetail
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface MaterialDetailState : MVIState {
    data object Loading : MaterialDetailState
    data class Loaded(val detail: MaterialDetail) : MaterialDetailState
    data class Error(val message: String) : MaterialDetailState
}

sealed interface MaterialDetailIntent : MVIIntent {
    data object Retry : MaterialDetailIntent
    data class DeleteLesson(val lessonId: String) : MaterialDetailIntent
}
