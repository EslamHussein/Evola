package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.lessons.LessonDetail
import evola.shared.lessons.LessonsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LessonDetailState {
    data object Loading : LessonDetailState
    data class Loaded(val detail: LessonDetail) : LessonDetailState
    data class Error(val message: String) : LessonDetailState
}

private const val POLL_INTERVAL_MS = 3000L

/** Polls while the lesson's own vocabulary extraction is still queued ("pending"), so a user who
 * lands here right after Processing/Resource Details sees the word count settle without a manual
 * refresh - mirrors [evola.composeapp.materials.MaterialDetailViewModel]'s poll-until-terminal
 * pattern, just against a lesson's own status instead of a material's. */
class LessonDetailViewModel(
    private val lessonId: String,
    private val repository: LessonsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LessonDetailState>(LessonDetailState.Loading)
    val state: StateFlow<LessonDetailState> = _state.asStateFlow()

    init {
        pollUntilReady()
    }

    private fun pollUntilReady() {
        viewModelScope.launch {
            while (true) {
                when (val result = repository.getLessonDetail(lessonId)) {
                    is ApiResult.Failure -> {
                        _state.value = LessonDetailState.Error(result.error.toUserMessage())
                        return@launch
                    }
                    is ApiResult.Success -> {
                        _state.value = LessonDetailState.Loaded(result.data)
                        if (result.data.status != "pending") return@launch
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
    }
}
