package evola.composeapp.feature.learning.vm

import androidx.lifecycle.ViewModel
import evola.composeapp.core.common.toUserMessage
import evola.shared.core.common.ApiResult
import evola.shared.feature.learning.domain.LessonsRepository
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

private const val POLL_INTERVAL_MS = 3000L

/** Polls while the lesson's own vocabulary extraction is still queued ("pending"), so a user who
 * lands here right after Processing/Resource Details sees the word count settle without a manual
 * refresh - mirrors [evola.composeapp.materials.MaterialDetailViewModel]'s poll-until-terminal
 * pattern, just against a lesson's own status instead of a material's. */
class LessonDetailViewModel(
    lessonId: String,
    repository: LessonsRepository,
) : ViewModel(), OrbitContainerHost<LessonDetailState, LessonDetailState, Nothing> {

    override val container = orbitContainer<LessonDetailState, Nothing>(LessonDetailState.Loading, onCreate = {
        while (true) {
            when (val result = repository.getLessonDetail(lessonId)) {
                is ApiResult.Failure -> {
                    reduce { LessonDetailState.Error(result.error.toUserMessage()) }
                    return@orbitContainer
                }
                is ApiResult.Success -> {
                    reduce { LessonDetailState.Loaded(result.data) }
                    if (result.data.status != "pending") return@orbitContainer
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    })
}
