package evola.shared.ai

import evola.shared.core.ApiResult
import evola.shared.segmentation.LessonSegmenter
import evola.shared.segmentation.RawSegment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    suspend fun segment(text: String): ApiResult<SegmentationResult> {
        LessonSegmenter.detectHeadings(text)?.let {
            return ApiResult.Success(SegmentationResult(LessonSegmenter.mergeAndCap(it), detectedLanguage = null, unsupported = false))
        }

        val collected = mutableListOf<RawSegment>()
        var detectedLanguage: String? = null
        val ranges = LessonSegmenter.chunkRanges(text.length)
        for ((index, range) in ranges.withIndex()) {
            val chunk = text.substring(range.first, range.last + 1)
            val parsed = when (val r = callChunk(chunk)) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> r.data
            }
            if (index == 0) {
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
        return ApiResult.Success(SegmentationResult(LessonSegmenter.mergeAndCap(collected), detectedLanguage, unsupported = false))
    }

    private suspend fun callChunk(chunkText: String): ApiResult<SegResultJson> =
        when (val r = client.complete(AnthropicModels.SMALL, 1500, SEGMENTATION_SYSTEM_PROMPT, "Content:\n$chunkText")) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val parsed = runCatching {
                    extractionJson.decodeFromString(SegResultJson.serializer(), normalizeModelJson(r.data))
                }.getOrNull()
                ApiResult.Success(parsed ?: SegResultJson())
            }
        }
}
