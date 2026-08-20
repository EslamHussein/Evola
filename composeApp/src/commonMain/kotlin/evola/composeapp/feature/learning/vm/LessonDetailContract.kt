package evola.composeapp.feature.learning.vm

import evola.shared.feature.learning.domain.LessonDetail

sealed interface LessonDetailState {
    data object Loading : LessonDetailState
    data class Loaded(val detail: LessonDetail) : LessonDetailState
    data class Error(val message: String) : LessonDetailState
}
