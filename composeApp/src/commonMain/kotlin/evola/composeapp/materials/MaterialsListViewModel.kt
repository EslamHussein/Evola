package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.getOrNull
import evola.shared.goals.GoalsRepository
import evola.shared.goals.Lesson
import evola.shared.materials.Material
import evola.shared.materials.MaterialsRepository
import evola.shared.todayLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MaterialsListState {
    data object Loading : MaterialsListState

    /** [currentLesson] is the same globally-next lesson Home's "Continue lesson" CTA resolves
     * (via [evola.shared.goals.GoalProgress.currentLessonId]) - Materials absorbed the old Study
     * tab's job, so it surfaces the same "what's next" signal here too, just as a card above the
     * book list instead of a whole separate screen. Null once every lesson is complete. */
    data class Loaded(val materials: List<Material>, val currentLesson: Lesson?) : MaterialsListState
    data class Error(val message: String) : MaterialsListState
}

class MaterialsListViewModel(
    private val goalId: String,
    private val goalsRepository: GoalsRepository,
    private val materialsRepository: MaterialsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MaterialsListState>(MaterialsListState.Loading)
    val state: StateFlow<MaterialsListState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = MaterialsListState.Loading
            _state.value = when (val materials = materialsRepository.list()) {
                is ApiResult.Failure -> MaterialsListState.Error(materials.error.toUserMessage())
                is ApiResult.Success -> {
                    // Best-effort, same as Home: the continue card just disappears if either call fails.
                    val progress = goalsRepository.getProgress(goalId, todayLocalDate()).getOrNull()
                    val lessons = goalsRepository.listLessons(goalId).getOrNull() ?: emptyList()
                    val currentLesson = progress?.currentLessonId?.let { id -> lessons.firstOrNull { it.id == id } }
                    MaterialsListState.Loaded(materials.data, currentLesson)
                }
            }
        }
    }
}
