package evola.shared.vocabulary

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
private data class VocabularyItemWireResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("mastery_state") val masteryState: String,
)

@Serializable
private data class VocabularySessionItemWireResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    @SerialName("drill_type") val drillType: String,
    val choices: List<String> = emptyList(),
)

@Serializable
private data class VocabularySessionWireResponse(
    @SerialName("session_id") val sessionId: String,
    val items: List<VocabularySessionItemWireResponse>,
    @SerialName("has_lesson_vocabulary") val hasLessonVocabulary: Boolean,
)

@Serializable
private data class VocabularyAnswerWireRequest(
    @SerialName("item_id") val itemId: String,
    val response: String,
    val correct: Boolean,
)

@Serializable
private data class VocabularyAnswerWireResponse(
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("next_review_at") val nextReviewAt: String,
    val resurfaced: Boolean,
)

@Serializable
private data class VocabularySessionCompleteWireResponse(
    @SerialName("items_count") val itemsCount: Int,
    val accuracy: Double,
)

class HttpVocabularyRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : VocabularyRepository {

    override suspend fun startOrResumeSession(accessToken: String, lessonId: String): VocabularySession? {
        val response = httpClient.post("$baseUrl/lessons/$lessonId/vocabulary/session") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.Created) return null
        val body = response.body<VocabularySessionWireResponse>()
        return VocabularySession(
            sessionId = body.sessionId,
            items = body.items.map { VocabularySessionItem(it.itemId, it.term, it.meaning, it.drillType, it.choices) },
            hasLessonVocabulary = body.hasLessonVocabulary,
        )
    }

    override suspend fun listVocabulary(accessToken: String, lessonId: String): List<VocabularyItem> {
        val response = httpClient.get("$baseUrl/lessons/$lessonId/vocabulary") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        return response.body<List<VocabularyItemWireResponse>>().map {
            VocabularyItem(it.itemId, it.term, it.meaning, it.gender, it.exampleSentence, it.masteryState)
        }
    }

    override suspend fun answer(
        accessToken: String,
        sessionId: String,
        itemId: String,
        response: String,
        correct: Boolean,
    ): VocabularyAnswerResult? {
        val httpResponse = httpClient.post("$baseUrl/vocabulary-sessions/$sessionId/answer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(VocabularyAnswerWireRequest(itemId, response, correct))
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<VocabularyAnswerWireResponse>()
        return VocabularyAnswerResult(body.masteryState, body.nextReviewAt, body.resurfaced)
    }

    override suspend fun complete(accessToken: String, sessionId: String): VocabularySessionSummary? {
        val httpResponse = httpClient.post("$baseUrl/vocabulary-sessions/$sessionId/complete") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<VocabularySessionCompleteWireResponse>()
        return VocabularySessionSummary(body.itemsCount, body.accuracy)
    }
}
