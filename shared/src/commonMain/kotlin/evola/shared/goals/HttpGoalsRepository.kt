package evola.shared.goals

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class CreateGoalWireRequest(@SerialName("goal_text") val goalText: String, val title: String?)

@Serializable
private data class UpdateGoalWireRequest(@SerialName("goal_text") val goalText: String?, val title: String?)

@Serializable
private data class GoalWireResponse(
    val id: String,
    @SerialName("goal_text") val goalText: String,
    val title: String?,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class LessonWireResponse(
    @SerialName("lesson_id") val lessonId: String,
    val number: Int,
    val title: String,
    val status: String,
    @SerialName("vocab_progress") val vocabProgress: Float = 0f,
    @SerialName("grammar_progress") val grammarProgress: Float = 0f,
    @SerialName("grammar_count") val grammarCount: Int = 0,
)

@Serializable
private data class GoalProgressWireResponse(
    @SerialName("overall_pct") val overallPct: Float,
    @SerialName("current_lesson_id") val currentLessonId: String? = null,
    @SerialName("streak_days") val streakDays: Int,
    @SerialName("today_completed") val todayCompleted: Boolean,
)

@Serializable
private data class WireErrorBody(val code: String, val message: String)

@Serializable
private data class WireErrorResponse(val error: WireErrorBody)

/** Data reads (getActiveGoal/listLessons/getProgress) return [ApiResult]; createGoal/updateGoal keep
 * their own sealed result types (they carry domain-specific outcomes a generic result would flatten)
 * and let a rare network exception surface to the caller, which already guards them. */
class HttpGoalsRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : GoalsRepository {

    override suspend fun createGoal(goalText: String, title: String?): CreateGoalResult {
        val response = client.post("$baseUrl/goals") {
            contentType(ContentType.Application.Json)
            setBody(CreateGoalWireRequest(goalText, title))
        }
        return when (response.status) {
            HttpStatusCode.Created -> CreateGoalResult.Success(response.body<GoalWireResponse>().toDomain())
            HttpStatusCode.Conflict -> CreateGoalResult.ActiveGoalExists
            HttpStatusCode.BadRequest -> CreateGoalResult.ValidationError(response.errorBody().message)
            else -> error("Create goal failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun updateGoal(goalId: String, goalText: String?, title: String?): UpdateGoalResult {
        val response = client.patch("$baseUrl/goals/$goalId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateGoalWireRequest(goalText, title))
        }
        return when (response.status) {
            HttpStatusCode.OK -> UpdateGoalResult.Success(response.body<GoalWireResponse>().toDomain())
            HttpStatusCode.NotFound -> UpdateGoalResult.NotFound
            HttpStatusCode.BadRequest -> UpdateGoalResult.ValidationError(response.errorBody().message)
            else -> error("Update goal failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun getActiveGoal(): ApiResult<Goal?> =
        when (val result = safeRequest<GoalWireResponse> { client.get("$baseUrl/goals/active") }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> {
                val error = result.error
                // 404 = "no active goal yet", a real state (→ onboarding), not a failure.
                if (error is DataError.Http && error.code == 404) ApiResult.Success(null) else result
            }
        }

    override suspend fun listLessons(goalId: String): ApiResult<List<Lesson>> =
        safeRequest<List<LessonWireResponse>> {
            client.get("$baseUrl/goals/$goalId/lessons")
        }.map { lessons -> lessons.map { it.toDomain() } }

    override suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress> =
        safeRequest<GoalProgressWireResponse> {
            client.get("$baseUrl/goals/$goalId/progress") { parameter("local_date", localDate) }
        }.map { it.toDomain() }

    private suspend fun HttpResponse.errorBody(): WireErrorBody = body<WireErrorResponse>().error

    private fun GoalWireResponse.toDomain() = Goal(id, goalText, title, isActive, createdAt)

    private fun LessonWireResponse.toDomain() = Lesson(lessonId, number, title, status, vocabProgress, grammarProgress, grammarCount)

    private fun GoalProgressWireResponse.toDomain() = GoalProgress(overallPct, currentLessonId, streakDays, todayCompleted)
}
