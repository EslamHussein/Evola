package evola.composeapp.main

import androidx.lifecycle.ViewModel
import evola.composeapp.core.common.toUserMessage
import evola.shared.core.common.ApiResult
import evola.shared.core.common.getOrNull
import evola.shared.goals.GoalsRepository
import evola.shared.todayLocalDate
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10) - a pure aggregation of M6/M7 progress.
 * Fetches the goal's progress and its lesson list together (the progress endpoint returns only a
 * `current_lesson_id`, so the lesson list is what resolves that id to a titled [Lesson] for the
 * "Continue Lesson N" CTA). Sends the device's own local date so streak/today are day-correct. */
class HomeViewModel(
    private val goalId: String,
    private val repository: GoalsRepository,
) : ViewModel(), OrbitContainerHost<HomeState, HomeState, Nothing> {

    override val container = orbitContainer<HomeState, Nothing>(HomeState.Loading, onCreate = { loadProgress() })

    fun refresh() = intent { loadProgress() }

    private suspend fun Syntax<HomeState, Nothing>.loadProgress() {
        reduce { HomeState.Loading }
        // Progress is the primary read; the lesson list is best-effort (only used to resolve the
        // current lesson's title for the CTA), so its failure degrades gracefully to no CTA.
        val newState = when (val progress = repository.getProgress(goalId, todayLocalDate())) {
            is ApiResult.Failure -> HomeState.Error(progress.error.toUserMessage())
            is ApiResult.Success -> {
                val lessons = repository.listLessons(goalId).getOrNull() ?: emptyList()
                val currentLesson = progress.data.currentLessonId?.let { id -> lessons.firstOrNull { it.id == id } }
                HomeState.Loaded(progress.data, currentLesson, hasLessons = lessons.isNotEmpty())
            }
        }
        reduce { newState }
    }
}
