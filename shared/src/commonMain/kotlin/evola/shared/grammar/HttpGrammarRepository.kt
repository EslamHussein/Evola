package evola.shared.grammar

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

class HttpGrammarRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : GrammarRepository {

    override suspend fun listTopics(accessToken: String, lessonId: String): List<GrammarTopic> {
        val response = httpClient.get("$baseUrl/lessons/$lessonId/grammar") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        return response.body<List<GrammarTopicWireResponse>>().map {
            GrammarTopic(it.topicId, it.name, it.explanation, it.masteryState)
        }
    }

    override suspend fun startOrResumeSession(accessToken: String, topicId: String): GrammarSession? {
        val response = httpClient.post("$baseUrl/grammar-topics/$topicId/exercise-session") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.Created) return null
        val body = response.body<GrammarSessionWireResponse>()
        return GrammarSession(
            sessionId = body.sessionId,
            topicName = body.topicName,
            exercises = body.exercises.map {
                GrammarExercise(it.exerciseId, it.type, it.prompt, it.answerKey, it.choices, it.answered)
            },
        )
    }

    override suspend fun answer(
        accessToken: String,
        sessionId: String,
        exerciseId: String,
        response: String,
        correct: Boolean,
    ): GrammarAnswerResult? {
        val httpResponse = httpClient.post("$baseUrl/grammar-sessions/$sessionId/answer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(GrammarAnswerWireRequest(exerciseId, response, correct))
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<GrammarAnswerWireResponse>()
        return GrammarAnswerResult(body.masteryState, body.nextReviewAt)
    }

    override suspend fun complete(accessToken: String, sessionId: String): GrammarSessionSummary? {
        val httpResponse = httpClient.post("$baseUrl/grammar-sessions/$sessionId/complete") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<GrammarSessionCompleteWireResponse>()
        return GrammarSessionSummary(body.exercisesCompleted, body.accuracy)
    }
}
