package evola.composeapp.lessons

import evola.shared.lessons.LessonDetail

sealed interface LessonDetailState {
    data object Loading : LessonDetailState
    data class Loaded(val detail: LessonDetail) : LessonDetailState
    data class Error(val message: String) : LessonDetailState
}
