package evola.composeapp.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.goals.GoalProgress
import evola.shared.goals.GoalsRepository
import evola.shared.goals.Lesson
import evola.shared.todayLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState

    /** [currentLesson] is resolved by cross-referencing [GoalProgress.currentLessonId] against the
     * fetched lesson list; null when every lesson is complete (or there are none). [hasLessons]
     * distinguishes the encouraging "upload something" empty state from a real dashboard. */
    data class Loaded(
        val progress: GoalProgress,
        val currentLesson: Lesson?,
        val hasLessons: Boolean,
    ) : HomeState

    data class Error(val message: String) : HomeState
}

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10) - a pure aggregation of M6/M7 progress.
 * Fetches the goal's progress and its lesson list together (the progress endpoint returns only a
 * `current_lesson_id`, so the lesson list is what resolves that id to a titled [Lesson] for the
 * "Continue Lesson N" CTA). Sends the device's own local date so streak/today are day-correct. */
class HomeViewModel(
    private val accessToken: String,
    private val goalId: String,
    private val repository: GoalsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val lessons = repository.listLessons(accessToken, goalId)
                val progress = repository.getProgress(accessToken, goalId, todayLocalDate())
                if (progress == null) {
                    _state.value = HomeState.Error("Couldn't load your progress.")
                    return@launch
                }
                val currentLesson = progress.currentLessonId?.let { id -> lessons.firstOrNull { it.id == id } }
                _state.value = HomeState.Loaded(progress, currentLesson, hasLessons = lessons.isNotEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Something went wrong.")
            }
        }
    }
}
