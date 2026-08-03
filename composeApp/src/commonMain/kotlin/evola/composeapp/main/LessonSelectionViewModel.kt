package evola.composeapp.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.goals.GoalsRepository
import evola.shared.goals.Lesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LessonSelectionState {
    data object Loading : LessonSelectionState
    data class Loaded(val lessons: List<Lesson>) : LessonSelectionState
    data class Error(val message: String) : LessonSelectionState
}

/** Lesson Selection List (01_PRODUCT_SPEC.md §1.7) - a simple one-shot fetch, refreshable, rather
 * than continuous polling like [evola.composeapp.materials.MaterialDetailViewModel]: this screen
 * doesn't need to watch a single in-flight job, just reflect whatever lessons already exist
 * whenever the user visits the Study tab. */
class LessonSelectionViewModel(
    private val accessToken: String,
    private val goalId: String,
    private val goalsRepository: GoalsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LessonSelectionState>(LessonSelectionState.Loading)
    val state: StateFlow<LessonSelectionState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = LessonSelectionState.Loading
            try {
                val lessons = goalsRepository.listLessons(accessToken, goalId)
                _state.value = LessonSelectionState.Loaded(lessons)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LessonSelectionState.Error(e.message ?: "Failed to load lessons.")
            }
        }
    }
}
