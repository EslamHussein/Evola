package evola.shared.goals

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

class HttpGoalsRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : GoalsRepository {

    override suspend fun createGoal(accessToken: String, goalText: String, title: String?): CreateGoalResult {
        val response = httpClient.post("$baseUrl/goals") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(CreateGoalWireRequest(goalText, title))
        }
        return when (response.status) {
            HttpStatusCode.Created -> CreateGoalResult.Success(response.body<GoalWireResponse>().toDomain())
            HttpStatusCode.Conflict -> CreateGoalResult.ActiveGoalExists
            HttpStatusCode.BadRequest -> CreateGoalResult.ValidationError(response.errorBody().message)
            else -> error("Create goal failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun updateGoal(accessToken: String, goalId: String, goalText: String?, title: String?): UpdateGoalResult {
        val response = httpClient.patch("$baseUrl/goals/$goalId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(UpdateGoalWireRequest(goalText, title))
        }
        return when (response.status) {
            HttpStatusCode.OK -> UpdateGoalResult.Success(response.body<GoalWireResponse>().toDomain())
            HttpStatusCode.NotFound -> UpdateGoalResult.NotFound
            HttpStatusCode.BadRequest -> UpdateGoalResult.ValidationError(response.errorBody().message)
            else -> error("Update goal failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun getActiveGoal(accessToken: String): Goal? {
        val response = httpClient.get("$baseUrl/goals/active") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        return response.body<GoalWireResponse>().toDomain()
    }

    override suspend fun listLessons(accessToken: String, goalId: String): List<Lesson> {
        val response = httpClient.get("$baseUrl/goals/$goalId/lessons") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        return response.body<List<LessonWireResponse>>().map { it.toDomain() }
    }

    override suspend fun getProgress(accessToken: String, goalId: String, localDate: String): GoalProgress? {
        val response = httpClient.get("$baseUrl/goals/$goalId/progress") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("local_date", localDate)
        }
        if (response.status != HttpStatusCode.OK) return null
        return response.body<GoalProgressWireResponse>().toDomain()
    }

    private suspend fun HttpResponse.errorBody(): WireErrorBody = body<WireErrorResponse>().error

    private fun GoalWireResponse.toDomain() = Goal(id, goalText, title, isActive, createdAt)

    private fun LessonWireResponse.toDomain() = Lesson(lessonId, number, title, status, vocabProgress, grammarProgress, grammarCount)

    private fun GoalProgressWireResponse.toDomain() = GoalProgress(overallPct, currentLessonId, streakDays, todayCompleted)
}
