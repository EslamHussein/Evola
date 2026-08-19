package evola.shared.ai

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.segmentation.LessonSegmenter
import evola.shared.segmentation.RawSegment
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MAX_CHUNK_ATTEMPTS = 3
private val RETRYABLE_HTTP = setOf(408, 425, 429, 500, 502, 503, 529)

data class SegmentationResult(
    val segments: List<RawSegment>,
    val detectedLanguage: String?,
    val unsupported: Boolean,
)

@Serializable
private data class SegLessonJson(
    val title: String,
    @SerialName("start_offset") val startOffset: Int,
    @SerialName("end_offset") val endOffset: Int,
    @SerialName("has_real_heading") val hasRealHeading: Boolean = false,
)

@Serializable
private data class SegResultJson(
    val lessons: List<SegLessonJson> = emptyList(),
    @SerialName("content_language_detected") val contentLanguageDetected: String? = null,
    @SerialName("unsupported_content") val unsupportedContent: Boolean = false,
)

private const val SEGMENTATION_SYSTEM_PROMPT =
    "You are segmenting a language-learning source document into discrete lessons.\n\n" +
        "Rules:\n" +
        "- Each lesson should be a coherent unit a learner could study in one sitting - target roughly 15-25 new vocabulary-worthy words per lesson. Split long sections, merge very short ones.\n" +
        "- Preserve the document's own structure where it exists (chapter/lesson headings). Only invent a title when no real heading is present.\n" +
        "- Never produce a lesson from content not in the target learning language, or from front/back matter (table of contents, index, publisher info).\n" +
        "- You are given ONE CHUNK of a larger document. start_offset/end_offset are character offsets INTO THE TEXT YOU WERE GIVEN in this message (0 = its first character), not the original document. A neighboring chunk overlaps slightly to compensate for abrupt boundaries.\n" +
        "- Output ONLY valid JSON. No prose, no markdown fences.\n\n" +
        "Output schema:\n{\"lessons\": [{\"title\": string, \"start_offset\": integer, \"end_offset\": integer, \"has_real_heading\": boolean}], \"content_language_detected\": string, \"unsupported_content\": boolean}"

/**
 * On-device lesson segmentation (04_AI_PROMPTS.md §1), ported from `LessonSegmentationWorker`.
 * Heuristic-first via [LessonSegmenter.detectHeadings] (no model call when confident); otherwise the
 * chunked LLM fallback, translating each chunk's local offsets to global before [LessonSegmenter.
 * mergeAndCap]. "entire" org-mode never calls this (a single synthetic lesson is materialized
 * directly). A first-chunk `unsupported_content` aborts with an empty, flagged result.
 */
class SegmentationExtractor(private val client: AnthropicClient) {

    suspend fun segment(text: String, onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null): ApiResult<SegmentationResult> {
        LessonSegmenter.detectHeadings(text)?.let {
            return ApiResult.Success(SegmentationResult(LessonSegmenter.mergeAndCap(it), detectedLanguage = null, unsupported = false))
        }

        val collected = mutableListOf<RawSegment>()
        var detectedLanguage: String? = null
        var firstProcessed = false
        var anyChunkSucceeded = false
        var lastFailure: ApiResult.Failure? = null
        val ranges = LessonSegmenter.chunkRanges(text.length)
        for (range in ranges) {
            val chunk = text.substring(range.first, range.last + 1)
            val parsed = when (val r = callChunk(chunk, onUsage)) {
                is ApiResult.Failure -> {
                    // A non-retryable client error (bad key, malformed request) is fatal for every
                    // chunk, so abort immediately. A transient failure that survived retries only
                    // costs us this one chunk — skip it and keep going (partial success, matching
                    // the retired server worker) rather than failing the whole document.
                    val err = r.error
                    if (err is DataError.Http && err.code in 400..499 && err.code !in RETRYABLE_HTTP) return r
                    lastFailure = r
                    continue
                }
                is ApiResult.Success -> r.data
            }
            anyChunkSucceeded = true
            if (!firstProcessed) {
                firstProcessed = true
                detectedLanguage = parsed.contentLanguageDetected
                if (parsed.unsupportedContent) {
                    return ApiResult.Success(SegmentationResult(emptyList(), detectedLanguage, unsupported = true))
                }
            }
            val base = range.first
            parsed.lessons.forEach { lesson ->
                val start = (base + lesson.startOffset).coerceIn(0, text.length)
                val end = (base + lesson.endOffset).coerceIn(start, text.length)
                if (end > start) {
                    collected.add(RawSegment(lesson.title.trim().ifEmpty { "Lesson" }, start, end, lesson.hasRealHeading))
                }
            }
        }
        // Only a total washout (every chunk failed) is a real error — surface it so the material is
        // marked FAILED and the user can retry. Otherwise proceed with whatever we segmented.
        if (!anyChunkSucceeded) return lastFailure ?: ApiResult.Failure(DataError.Unexpected)
        return ApiResult.Success(SegmentationResult(LessonSegmenter.mergeAndCap(collected), detectedLanguage, unsupported = false))
    }

    /** One chunk, retried up to [MAX_CHUNK_ATTEMPTS] with backoff on transient failures (network /
     * timeout / 429 / 5xx) — a single hiccup among dozens of sequential chunk calls shouldn't fail
     * the document. A non-retryable HTTP error (e.g. 401 bad key) returns immediately. */
    private suspend fun callChunk(chunkText: String, onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)?): ApiResult<SegResultJson> {
        var lastFailure: ApiResult.Failure = ApiResult.Failure(DataError.Unexpected)
        repeat(MAX_CHUNK_ATTEMPTS) { attempt ->
            when (val r = client.complete(AnthropicModels.SMALL, 1500, SEGMENTATION_SYSTEM_PROMPT, "Content:\n$chunkText", onUsage)) {
                is ApiResult.Success -> {
                    val parsed = runCatching {
                        extractionJson.decodeFromString(SegResultJson.serializer(), normalizeModelJson(r.data))
                    }.getOrNull()
                    return ApiResult.Success(parsed ?: SegResultJson())
                }
                is ApiResult.Failure -> {
                    lastFailure = r
                    val err = r.error
                    val retryable = err is DataError.Network || (err is DataError.Http && err.code in RETRYABLE_HTTP)
                    if (!retryable) return r
                    if (attempt < MAX_CHUNK_ATTEMPTS - 1) delay(600L * (attempt + 1))
                }
            }
        }
        return lastFailure
    }
}
