package evola.shared.vocabulary

import evola.shared.core.ApiResult
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    private val client: HttpClient,
    private val baseUrl: String,
) : VocabularyRepository {

    override suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularyPack> =
        safeRequest<VocabularyPackWireResponse> {
            client.post("$baseUrl/lessons/$lessonId/vocabulary/session")
        }.map { it.toDomain() }

    override suspend fun listVocabulary(lessonId: String): ApiResult<List<VocabularyItem>> =
        safeRequest<List<VocabularyItemWireResponse>> {
            client.get("$baseUrl/lessons/$lessonId/vocabulary")
        }.map { items -> items.map { it.toDomain() } }

    override suspend fun answer(
        packId: String,
        itemId: String,
        stageIndex: Int,
        response: String,
    ): ApiResult<VocabularyStageAnswerResult> =
        safeRequest<VocabularyAnswerWireResponse> {
            client.post("$baseUrl/vocabulary-sessions/$packId/answer") {
                contentType(ContentType.Application.Json)
                setBody(VocabularyAnswerWireRequest(itemId, stageIndex, response))
            }
        }.map { VocabularyStageAnswerResult(it.correct, it.feedback, it.next?.toDomain()) }

    override suspend fun complete(packId: String, localDate: String): ApiResult<VocabularyPackSummary> =
        safeRequest<VocabularyPackCompleteWireResponse> {
            client.post("$baseUrl/vocabulary-sessions/$packId/complete") {
                contentType(ContentType.Application.Json)
                setBody(SessionCompleteWireRequest(localDate))
            }
        }.map { VocabularyPackSummary(it.wordsLearned, it.accuracy, it.timeSeconds) }

    override suspend fun updateFlags(
        itemId: String,
        isBookmarked: Boolean?,
        markedDifficult: Boolean?,
    ): ApiResult<VocabularyItem> =
        safeRequest<VocabularyItemWireResponse> {
            client.patch("$baseUrl/vocabulary-items/$itemId/flags") {
                contentType(ContentType.Application.Json)
                setBody(VocabularyFlagsWireRequest(isBookmarked, markedDifficult))
            }
        }.map { it.toDomain() }
}
