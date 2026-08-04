package evola.shared.vocabulary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
    @SerialName("meaning_ar") val meaningAr: String? = null,
    @SerialName("ipa_pronunciation") val ipaPronunciation: String? = null,
    @SerialName("related_words") val relatedWords: List<String> = emptyList(),
    @SerialName("difficulty_rating") val difficultyRating: String? = null,
    @SerialName("frequency_rating") val frequencyRating: String? = null,
    @SerialName("memory_tip") val memoryTip: String? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("marked_difficult") val markedDifficult: Boolean = false,
) {
    fun toDomain() = VocabularyItem(
        itemId, term, meaning, gender, exampleSentence, masteryState, meaningAr, ipaPronunciation,
        relatedWords, difficultyRating, frequencyRating, memoryTip, isBookmarked, markedDifficult,
    )
}

@Serializable
private data class PackWordWireResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("meaning_ar") val meaningAr: String? = null,
    @SerialName("ipa_pronunciation") val ipaPronunciation: String? = null,
    @SerialName("related_words") val relatedWords: List<String> = emptyList(),
    @SerialName("difficulty_rating") val difficultyRating: String? = null,
    @SerialName("frequency_rating") val frequencyRating: String? = null,
    @SerialName("memory_tip") val memoryTip: String? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("marked_difficult") val markedDifficult: Boolean = false,
    @SerialName("recognition_choices") val recognitionChoices: List<String> = emptyList(),
    @SerialName("partial_mask") val partialMask: String? = null,
    @SerialName("sentence_with_blank") val sentenceWithBlank: String? = null,
    @SerialName("sentence_translation_prompt") val sentenceTranslationPrompt: String? = null,
) {
    fun toDomain() = PackWord(
        itemId, term, meaning, gender, exampleSentence, masteryState, meaningAr, ipaPronunciation,
        relatedWords, difficultyRating, frequencyRating, memoryTip, isBookmarked, markedDifficult,
        recognitionChoices, partialMask, sentenceWithBlank, sentenceTranslationPrompt,
    )
}

@Serializable
private data class VocabularyPackWireResponse(
    @SerialName("pack_id") val packId: String,
    @SerialName("pack_number") val packNumber: Int,
    @SerialName("word_index") val wordIndex: Int,
    @SerialName("words_count") val wordsCount: Int,
    @SerialName("stage_index") val stageIndex: Int,
    val word: PackWordWireResponse,
    @SerialName("ready_to_complete") val readyToComplete: Boolean = false,
) {
    fun toDomain() = VocabularyPack(packId, packNumber, wordIndex, wordsCount, stageIndex, word.toDomain(), readyToComplete)
}

@Serializable
private data class VocabularyAnswerWireRequest(
    @SerialName("item_id") val itemId: String,
    @SerialName("stage_index") val stageIndex: Int,
    val response: String,
)

@Serializable
private data class VocabularyAnswerWireResponse(
    val correct: Boolean? = null,
    val feedback: String? = null,
    val next: VocabularyPackWireResponse? = null,
)

@Serializable
private data class VocabularyPackCompleteWireResponse(
    @SerialName("words_learned") val wordsLearned: Int,
    val accuracy: Double,
    @SerialName("time_seconds") val timeSeconds: Long,
)

@Serializable
private data class VocabularyFlagsWireRequest(
    @SerialName("is_bookmarked") val isBookmarked: Boolean? = null,
    @SerialName("marked_difficult") val markedDifficult: Boolean? = null,
)

@Serializable
private data class SessionCompleteWireRequest(@SerialName("local_date") val localDate: String)

class HttpVocabularyRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : VocabularyRepository {

    override suspend fun startOrResumeSession(accessToken: String, lessonId: String): VocabularyPack? {
        val response = httpClient.post("$baseUrl/lessons/$lessonId/vocabulary/session") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.Created) return null
        return response.body<VocabularyPackWireResponse>().toDomain()
    }

    override suspend fun listVocabulary(accessToken: String, lessonId: String): List<VocabularyItem> {
        val response = httpClient.get("$baseUrl/lessons/$lessonId/vocabulary") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return emptyList()
        return response.body<List<VocabularyItemWireResponse>>().map { it.toDomain() }
    }

    override suspend fun answer(
        accessToken: String,
        packId: String,
        itemId: String,
        stageIndex: Int,
        response: String,
    ): VocabularyStageAnswerResult? {
        val httpResponse = httpClient.post("$baseUrl/vocabulary-sessions/$packId/answer") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(VocabularyAnswerWireRequest(itemId, stageIndex, response))
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<VocabularyAnswerWireResponse>()
        return VocabularyStageAnswerResult(body.correct, body.feedback, body.next?.toDomain())
    }

    override suspend fun complete(accessToken: String, packId: String, localDate: String): VocabularyPackSummary? {
        val httpResponse = httpClient.post("$baseUrl/vocabulary-sessions/$packId/complete") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(SessionCompleteWireRequest(localDate))
        }
        if (httpResponse.status != HttpStatusCode.OK) return null
        val body = httpResponse.body<VocabularyPackCompleteWireResponse>()
        return VocabularyPackSummary(body.wordsLearned, body.accuracy, body.timeSeconds)
    }

    override suspend fun updateFlags(
        accessToken: String,
        itemId: String,
        isBookmarked: Boolean?,
        markedDifficult: Boolean?,
    ): VocabularyItem? {
        val response = httpClient.patch("$baseUrl/vocabulary-items/$itemId/flags") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(VocabularyFlagsWireRequest(isBookmarked, markedDifficult))
        }
        if (response.status != HttpStatusCode.OK) return null
        return response.body<VocabularyItemWireResponse>().toDomain()
    }
}
