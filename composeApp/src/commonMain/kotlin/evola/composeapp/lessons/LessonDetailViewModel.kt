package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.lessons.LessonDetail
import evola.shared.lessons.LessonsRepository
import kotlinx.coroutines.CancellationException
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
    private val accessToken: String,
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
                val detail = try {
                    repository.getLessonDetail(accessToken, lessonId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = LessonDetailState.Error(e.message ?: "Failed to load lesson.")
                    return@launch
                }
                if (detail == null) {
                    _state.value = LessonDetailState.Error("Lesson not found.")
                    return@launch
                }
                _state.value = LessonDetailState.Loaded(detail)
                if (detail.status != "pending") break
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
