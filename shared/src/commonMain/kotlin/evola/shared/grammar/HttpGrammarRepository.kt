package evola.shared.grammar

import evola.shared.core.ApiResult
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class GrammarTopicWireResponse(
    @SerialName("topic_id") val topicId: String,
    val name: String,
    val explanation: String,
    @SerialName("mastery_state") val masteryState: String,
)

@Serializable
private data class GrammarExerciseWireResponse(
    @SerialName("exercise_id") val exerciseId: String,
    val type: String,
    val prompt: String,
    @SerialName("answer_key") val answerKey: String,
    val choices: List<String> = emptyList(),
    val answered: Boolean = false,
)

@Serializable
private data class GrammarSessionWireResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("topic_name") val topicName: String,
    val exercises: List<GrammarExerciseWireResponse>,
)

@Serializable
private data class GrammarAnswerWireRequest(
    @SerialName("exercise_id") val exerciseId: String,
    val response: String,
    val correct: Boolean,
)

@Serializable
private data class GrammarAnswerWireResponse(
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("next_review_at") val nextReviewAt: String,
)

@Serializable
private data class GrammarSessionCompleteWireResponse(
    @SerialName("exercises_completed") val exercisesCompleted: Int,
    val accuracy: Double,
)

@Serializable
private data class SessionCompleteWireRequest(@SerialName("local_date") val localDate: String)

/** Uses the shared authenticated client (bearer token attached by the Ktor Auth plugin), so no
 * access token is threaded in; every call is mapped to an [ApiResult] at the [safeRequest]
 * boundary. */
class HttpGrammarRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : GrammarRepository {

    override suspend fun listTopics(lessonId: String): ApiResult<List<GrammarTopic>> =
        safeRequest<List<GrammarTopicWireResponse>> {
            client.get("$baseUrl/lessons/$lessonId/grammar")
        }.map { topics -> topics.map { GrammarTopic(it.topicId, it.name, it.explanation, it.masteryState) } }

    override suspend fun startOrResumeSession(topicId: String): ApiResult<GrammarSession> =
        safeRequest<GrammarSessionWireResponse> {
            client.post("$baseUrl/grammar-topics/$topicId/exercise-session")
        }.map { body ->
            GrammarSession(
                sessionId = body.sessionId,
                topicName = body.topicName,
                exercises = body.exercises.map {
                    GrammarExercise(it.exerciseId, it.type, it.prompt, it.answerKey, it.choices, it.answered)
                },
            )
        }

    override suspend fun answer(
        sessionId: String,
        exerciseId: String,
        response: String,
        correct: Boolean,
    ): ApiResult<GrammarAnswerResult> =
        safeRequest<GrammarAnswerWireResponse> {
            client.post("$baseUrl/grammar-sessions/$sessionId/answer") {
                contentType(ContentType.Application.Json)
                setBody(GrammarAnswerWireRequest(exerciseId, response, correct))
            }
        }.map { GrammarAnswerResult(it.masteryState, it.nextReviewAt) }

    override suspend fun complete(sessionId: String, localDate: String): ApiResult<GrammarSessionSummary> =
        safeRequest<GrammarSessionCompleteWireResponse> {
            client.post("$baseUrl/grammar-sessions/$sessionId/complete") {
                contentType(ContentType.Application.Json)
                setBody(SessionCompleteWireRequest(localDate))
            }
        }.map { GrammarSessionSummary(it.exercisesCompleted, it.accuracy) }
}
