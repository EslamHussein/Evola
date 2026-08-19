package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.ApiResult
import evola.shared.lessons.LessonsRepository
import kotlinx.coroutines.delay
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.asyncInit
import pro.respawn.flowmvi.plugins.reduce

private const val POLL_INTERVAL_MS = 3000L

/** Polls while the lesson's own vocabulary extraction is still queued ("pending"), so a user who
 * lands here right after Processing/Resource Details sees the word count settle without a manual
 * refresh - mirrors [evola.composeapp.materials.MaterialDetailContainer]'s poll-until-terminal
 * pattern, just against a lesson's own status instead of a material's. */
class LessonDetailContainer(
    private val lessonId: String,
    private val repository: LessonsRepository,
) : Container<LessonDetailState, LessonDetailIntent, Nothing> {

    override val store = store(initial = LessonDetailState.Loading) {
        configure { name = "LessonDetailStore" }
        asyncInit {
            while (true) {
                when (val result = repository.getLessonDetail(lessonId)) {
                    is ApiResult.Failure -> {
                        updateState { LessonDetailState.Error(result.error.toUserMessage()) }
                        return@asyncInit
                    }
                    is ApiResult.Success -> {
                        updateState { LessonDetailState.Loaded(result.data) }
                        if (result.data.status != "pending") return@asyncInit
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
        reduce { }
    }
}
