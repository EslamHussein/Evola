package evola.server

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val POLL_INTERVAL_MS = 30_000L
private const val MAX_CHUNK_ATTEMPTS = 3
private const val BACKOFF_BASE_MS = 1000L

private const val SEGMENTATION_SYSTEM_PROMPT =
    "You are segmenting a language-learning source document into discrete lessons.\n\n" +
        "Rules:\n" +
        "- Each lesson should be a coherent unit a learner could study in one sitting - target roughly " +
        "15-25 new vocabulary-worthy words per lesson. Split long sections, merge very short ones.\n" +
        "- Preserve the document's own structure where it exists (chapter/lesson headings). Only invent " +
        "a title when no real heading is present.\n" +
        "- Never produce a lesson from content that is not in the target learning language, or from " +
        "front/back matter (table of contents, index, publisher information, acknowledgements).\n" +
        "- You are given ONE CHUNK of a larger document, not the whole thing. start_offset/end_offset " +
        "are character offsets INTO THE TEXT YOU WERE GIVEN in this message (0 = its first character), " +
        "not the original document. It's fine if a lesson appears to start or end abruptly at a chunk " +
        "boundary - a neighboring chunk overlaps slightly with this one to compensate.\n" +
        "- Output ONLY valid JSON. No prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"lessons\": [{\"title\": string, \"start_offset\": integer, \"end_offset\": integer, " +
        "\"has_real_heading\": boolean}], \"content_language_detected\": string, " +
        "\"unsupported_content\": boolean}"

@Serializable
private data class SegmentationLessonJson(
    val title: String,
    @SerialName("start_offset") val startOffset: Int,
    @SerialName("end_offset") val endOffset: Int,
    @SerialName("has_real_heading") val hasRealHeading: Boolean,
)

@Serializable
private data class SegmentationResultJson(
    val lessons: List<SegmentationLessonJson> = emptyList(),
    @SerialName("content_language_detected") val contentLanguageDetected: String? = null,
    @SerialName("unsupported_content") val unsupportedContent: Boolean = false,
)

@Serializable
private data class RangeJson(val start: Int, val end: Int)

private data class ClaimedJob(
    val id: UUID,
    val contentHash: String,
    val contentText: String,
    val isResume: Boolean,
    val rangesToRetry: List<IntRange>,
    val previousSegments: List<RawSegment>,
    val previousDetectedLanguage: String?,
)

/**
 * Async job worker (spec §1.6, 04_AI_PROMPTS.md §1): polls `extraction_jobs` for QUEUED rows and
 * splits each material's text into `lessons` rows instead of the old single-shot
 * vocabulary+grammar+exercises blob (retired - see V7 migration). Tries a cheap regex
 * heading-detection pass first ([LessonSegmenter.detectHeadings]); falls back to chunked LLM
 * calls otherwise, retrying each chunk up to [MAX_CHUNK_ATTEMPTS] times with backoff before giving
 * up on that chunk only (partial success - other chunks still proceed). `extraction_cache` is
 * reused as the content-hash-keyed segmentation cache, same dedup pattern as before.
 */
class LessonSegmentationWorker(
    private val database: Database,
    private val client: AnthropicClient,
    private val model: String = "claude-haiku-4-5",
) {
    val wake = Channel<Unit>(Channel.CONFLATED)

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                val processed = runCatching { processNextJob() }.getOrElse { false }
                if (!processed) {
                    withTimeoutOrNull(POLL_INTERVAL_MS) { wake.receive() }
                }
            }
        }
    }

    private suspend fun processNextJob(): Boolean {
        val claimed = claimNextQueuedJob() ?: return false
        segmentAndStore(claimed)
        return true
    }

    private suspend fun claimNextQueuedJob(): ClaimedJob? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = ExtractionJobsTable
                .selectAll().where { ExtractionJobsTable.status eq "QUEUED" }
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction null

            val jobId = row[ExtractionJobsTable.id]
            val contentHash = row[ExtractionJobsTable.contentHash]
            val storedFailedRanges = row[ExtractionJobsTable.failedRanges]

            val claimedCount = ExtractionJobsTable.update({
                (ExtractionJobsTable.id eq jobId) and (ExtractionJobsTable.status eq "QUEUED")
            }) {
                it[status] = "PROCESSING"
                it[updatedAt] = Instant.now()
            }
            if (claimedCount == 0) return@newSuspendedTransaction null

            MaterialsTable.update({ MaterialsTable.contentHash eq contentHash }) { it[status] = "PROCESSING" }

            val isResume = storedFailedRanges != null
            val cacheRow = ExtractionCacheTable.selectAll().where { ExtractionCacheTable.contentHash eq contentHash }.singleOrNull()
            val previousSegments = if (isResume && cacheRow != null) {
                MATERIALS_JSON.decodeFromString(ListSerializer(RawSegmentJson.serializer()), cacheRow[ExtractionCacheTable.segments])
                    .map { it.toRaw() }
            } else {
                emptyList()
            }

            ClaimedJob(
                id = jobId,
                contentHash = contentHash,
                contentText = row[ExtractionJobsTable.contentText],
                isResume = isResume,
                rangesToRetry = if (isResume) decodeRanges(storedFailedRanges!!) else emptyList(),
                previousSegments = previousSegments,
                previousDetectedLanguage = cacheRow?.get(ExtractionCacheTable.detectedLanguage),
            )
        }

    private suspend fun segmentAndStore(job: ClaimedJob) {
        if (!job.isResume) {
            val heuristicSegments = LessonSegmenter.detectHeadings(job.contentText)
            if (heuristicSegments != null) {
                finalizeSuccess(job.contentHash, LessonSegmenter.mergeAndCap(heuristicSegments), detectedLanguage = null)
                return
            }
        }
        val ranges = if (job.isResume) job.rangesToRetry else LessonSegmenter.chunkRanges(job.contentText.length)
        segmentViaLlmChunks(job, ranges)
    }

    private suspend fun segmentViaLlmChunks(job: ClaimedJob, ranges: List<IntRange>) {
        val allSegments = job.previousSegments.toMutableList()
        val failedRanges = mutableListOf<IntRange>()
        var detectedLanguage = job.previousDetectedLanguage
        var chunksParsed = 0
        var chunksFlaggedUnsupported = 0

        for (range in ranges) {
            val chunkText = job.contentText.substring(range.first, range.last + 1)
            var parsed: SegmentationResultJson? = null
            var attempt = 0
            while (attempt < MAX_CHUNK_ATTEMPTS && parsed == null) {
                attempt++
                parsed = callModel(chunkText, job.id)
                if (parsed == null && attempt < MAX_CHUNK_ATTEMPTS) {
                    delay(BACKOFF_BASE_MS * (1L shl (attempt - 1)))
                }
            }

            if (parsed == null) {
                failedRanges.add(range)
                continue
            }
            chunksParsed++
            if (parsed.unsupportedContent) {
                // A single chunk being front/back matter (title page, copyright, TOC) doesn't mean
                // the whole document is unsupported - just contributes no lessons, same as any
                // other chunk with nothing extractable. Only bail entirely below if EVERY parsed
                // chunk agreed and nothing usable was found anywhere in the document.
                chunksFlaggedUnsupported++
                continue
            }
            detectedLanguage = parsed.contentLanguageDetected ?: detectedLanguage
            parsed.lessons.forEach { lesson ->
                allSegments.add(
                    RawSegment(
                        title = lesson.title,
                        startOffset = range.first + lesson.startOffset,
                        endOffset = range.first + lesson.endOffset,
                        hasRealHeading = lesson.hasRealHeading,
                    ),
                )
            }
        }

        if (chunksParsed > 0 && chunksFlaggedUnsupported == chunksParsed && allSegments.isEmpty()) {
            markUnsupported(job.contentHash)
            return
        }

        val merged = LessonSegmenter.mergeAndCap(allSegments)
        if (failedRanges.isEmpty()) {
            finalizeSuccess(job.contentHash, merged, detectedLanguage)
        } else {
            markPartiallyFailed(job.id, job.contentHash, failedRanges, merged, detectedLanguage)
        }
    }

    private suspend fun callModel(chunkText: String, jobId: UUID): SegmentationResultJson? = try {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(1500L)
            .system(SEGMENTATION_SYSTEM_PROMPT)
            .addUserMessage("Content:\n$chunkText")
            .build()
        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        logModelCall(response, jobId)
        val raw = extractText(response)
        raw?.let { runCatching { MATERIALS_JSON.decodeFromString(SegmentationResultJson.serializer(), it) }.getOrNull() }
    } catch (e: Exception) {
        null
    }

    private suspend fun logModelCall(response: Message, jobId: UUID) {
        val inputTokens = response.usage().inputTokens().toInt()
        val outputTokens = response.usage().outputTokens().toInt()
        // claude-haiku-4-5 pricing, per-1M-token USD - observability estimate, not billing-grade.
        val cost = (inputTokens / 1_000_000.0) * 1.00 + (outputTokens / 1_000_000.0) * 5.00

        newSuspendedTransaction(Dispatchers.IO, database) {
            ModelCallLogTable.insert {
                it[id] = UUID.randomUUID()
                it[taskType] = "LESSON_SEGMENTATION"
                it[modelTier] = "SMALL"
                it[this.inputTokens] = inputTokens
                it[this.outputTokens] = outputTokens
                it[costEstimate] = cost
                it[cacheHit] = false
                it[materialId] = null
                it[userId] = null
                it[extractionJobId] = jobId
                it[createdAt] = Instant.now()
            }
        }
    }

    private suspend fun finalizeSuccess(contentHash: String, segments: List<RawSegment>, detectedLanguage: String?) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            writeCacheAndLessons(contentHash, segments, detectedLanguage, unsupportedContent = false)
            ExtractionJobsTable.update({ ExtractionJobsTable.contentHash eq contentHash }) {
                it[status] = "DONE"
                it[error] = null
                it[failedRanges] = null
                it[updatedAt] = Instant.now()
            }
            MaterialsTable.update({ MaterialsTable.contentHash eq contentHash }) { it[status] = "READY" }
        }
    }

    private suspend fun markPartiallyFailed(
        jobId: UUID,
        contentHash: String,
        failedRanges: List<IntRange>,
        mergedSegments: List<RawSegment>,
        detectedLanguage: String?,
    ) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            if (mergedSegments.isNotEmpty()) {
                writeCacheAndLessons(contentHash, mergedSegments, detectedLanguage, unsupportedContent = false)
            }
            ExtractionJobsTable.update({ ExtractionJobsTable.id eq jobId }) {
                it[status] = "FAILED"
                it[error] = "Segmentation failed for ${failedRanges.size} chunk(s) after $MAX_CHUNK_ATTEMPTS retries."
                it[this.failedRanges] = encodeRanges(failedRanges)
                it[updatedAt] = Instant.now()
            }
            MaterialsTable.update({ MaterialsTable.contentHash eq contentHash }) { it[status] = "FAILED" }
        }
    }

    private suspend fun markUnsupported(contentHash: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val existingId = ExtractionCacheTable.selectAll().where { ExtractionCacheTable.contentHash eq contentHash }
                .singleOrNull()?.get(ExtractionCacheTable.id)
            if (existingId != null) {
                ExtractionCacheTable.update({ ExtractionCacheTable.id eq existingId }) {
                    it[segments] = "[]"
                    it[unsupportedContent] = true
                }
            } else {
                ExtractionCacheTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.contentHash] = contentHash
                    it[segments] = "[]"
                    it[detectedLanguage] = null
                    it[unsupportedContent] = true
                    it[modelVersion] = model
                    it[createdAt] = Instant.now()
                }
            }
            ExtractionJobsTable.update({ ExtractionJobsTable.contentHash eq contentHash }) {
                it[status] = "DONE"
                it[error] = null
                it[failedRanges] = null
                it[updatedAt] = Instant.now()
            }
            MaterialsTable.update({ MaterialsTable.contentHash eq contentHash }) { it[status] = "UNSUPPORTED_CONTENT" }
        }
    }

    /** Idempotent: always deletes and re-inserts every affected material's lessons from the full
     * merged segment list, so a resumed job (partial retry) never duplicates already-created rows. */
    private fun writeCacheAndLessons(
        contentHash: String,
        segments: List<RawSegment>,
        detectedLanguage: String?,
        unsupportedContent: Boolean,
    ) {
        val segmentsJson = MATERIALS_JSON.encodeToString(ListSerializer(RawSegmentJson.serializer()), segments.map { it.toJson() })
        val existingId = ExtractionCacheTable.selectAll().where { ExtractionCacheTable.contentHash eq contentHash }
            .singleOrNull()?.get(ExtractionCacheTable.id)

        if (existingId != null) {
            ExtractionCacheTable.update({ ExtractionCacheTable.id eq existingId }) {
                it[this.segments] = segmentsJson
                it[this.detectedLanguage] = detectedLanguage
                it[this.unsupportedContent] = unsupportedContent
            }
        } else {
            ExtractionCacheTable.insert {
                it[id] = UUID.randomUUID()
                it[this.contentHash] = contentHash
                it[this.segments] = segmentsJson
                it[this.detectedLanguage] = detectedLanguage
                it[this.unsupportedContent] = unsupportedContent
                it[modelVersion] = model
                it[createdAt] = Instant.now()
            }
        }

        if (segments.isEmpty()) return

        val affectedMaterials = MaterialsTable.selectAll().where { MaterialsTable.contentHash eq contentHash }
            .map { it[MaterialsTable.id] to it[MaterialsTable.goalId] }

        for ((materialId, goalId) in affectedMaterials) {
            LessonsTable.deleteWhere { builder -> builder.run { LessonsTable.materialId eq materialId } }
            segments.forEachIndexed { index, segment ->
                LessonsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.materialId] = materialId
                    it[this.goalId] = goalId
                    it[number] = index + 1
                    it[title] = segment.title.take(150)
                    it[status] = "pending"
                    it[sourceTextRef] = "${segment.startOffset}:${segment.endOffset}"
                    it[createdAt] = Instant.now()
                }
            }
        }
    }

    private fun encodeRanges(ranges: List<IntRange>): String =
        MATERIALS_JSON.encodeToString(ListSerializer(RangeJson.serializer()), ranges.map { RangeJson(it.first, it.last) })

    private fun decodeRanges(json: String): List<IntRange> =
        MATERIALS_JSON.decodeFromString(ListSerializer(RangeJson.serializer()), json).map { it.start..it.end }

    private fun extractText(response: Message): String? =
        response.content()
            .asSequence()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .firstOrNull()
            .let { normalizeJsonText(it) }

    /** The model occasionally wraps otherwise-valid JSON in markdown fences despite instructions not
     * to - strip them rather than failing the whole chunk over a formatting slip. */
    private fun normalizeJsonText(text: String?): String? =
        text?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
}
