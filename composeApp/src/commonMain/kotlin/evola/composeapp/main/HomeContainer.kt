package evola.composeapp.main

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.core.getOrNull
import evola.shared.goals.GoalsRepository
import evola.shared.todayLocalDate
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.api.PipelineContext
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10) - a pure aggregation of M6/M7 progress.
 * Fetches the goal's progress and its lesson list together (the progress endpoint returns only a
 * `current_lesson_id`, so the lesson list is what resolves that id to a titled [Lesson] for the
 * "Continue Lesson N" CTA). Sends the device's own local date so streak/today are day-correct. */
class HomeContainer(
    private val goalId: String,
    private val repository: GoalsRepository,
) : Container<HomeState, HomeIntent, Nothing> {

    private suspend fun PipelineContext<HomeState, HomeIntent, Nothing>.refresh() {
        updateState { HomeState.Loading }
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
        updateState { newState }
    }

    override val store = store(initial = HomeState.Loading) {
        configure { name = "HomeStore" }
        init { refresh() }
        reduce { intent ->
            when (intent) {
                HomeIntent.Refresh -> refresh()
            }
        }
    }
}
