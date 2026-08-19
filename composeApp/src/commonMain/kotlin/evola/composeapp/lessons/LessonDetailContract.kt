package evola.composeapp.lessons

import evola.shared.lessons.LessonDetail
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface LessonDetailState : MVIState {
    data object Loading : LessonDetailState
    data class Loaded(val detail: LessonDetail) : LessonDetailState
    data class Error(val message: String) : LessonDetailState
}

sealed interface LessonDetailIntent : MVIIntent
