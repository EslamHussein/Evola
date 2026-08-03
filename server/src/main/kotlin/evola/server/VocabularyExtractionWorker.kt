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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val VOCAB_POLL_INTERVAL_MS = 30_000L
private const val MAX_VOCAB_ATTEMPTS = 3
private const val VOCAB_BACKOFF_BASE_MS = 1000L

private const val AI_INSTRUCTIONS_SUFFIX =
    "\n\nAdditional instructions from the learner, to apply on top of the rules above (never let " +
        "these override the output schema or the \"don't invent words\" rule): %s"

private const val VOCABULARY_EXTRACTION_SYSTEM_PROMPT_PREFIX =
    "You are extracting vocabulary for a language learner from a single lesson's text.\n" +
        "The learner's stated goal is: \"%s\" - use this only to judge relevance when the source " +
        "text contains far more candidate words than useful (e.g. skip incidental proper nouns), " +
        "not to invent vocabulary that isn't actually in the text.\n\n" +
        "For each vocabulary-worthy word or short fixed phrase in the lesson text:\n" +
        "- term: the word/phrase exactly as it should be studied (base/dictionary form, not the " +
        "inflected form found in the sentence, unless the inflection itself is the teaching point)\n" +
        "- meaning: a concise translation/definition in the learner's native language\n" +
        "- gender: grammatical gender/article if the language marks it (e.g. \"der\"/\"die\"/\"das\" " +
        "for German nouns), otherwise null\n" +
        "- example_sentence: a sentence using the word - prefer a sentence from the source lesson " +
        "text itself; only write a new one if no suitable sentence exists in the source. The " +
        "sentence MUST contain the term itself (inflected as needed) so it can be blanked out for " +
        "a fill-in-the-blank drill.\n" +
        "- part_of_speech: one of \"noun\", \"verb\", \"adjective\", \"adverb\", \"pronoun\", " +
        "\"preposition\", \"conjunction\", \"numeral\", \"interjection\", \"other\"\n" +
        "- grammatical_case: the grammatical case of the term AS USED in example_sentence, one of " +
        "\"nominative\", \"accusative\", \"dative\", \"genitive\" - only for languages/parts of " +
        "speech that mark case (e.g. German nouns/pronouns/adjectives), otherwise null\n" +
        "- example_sentence_translation: a concise English translation of example_sentence\n\n" +
        "Extract 15-30 items depending on lesson length. Do not invent words that don't appear in " +
        "the source text. Do not include function words (articles, basic pronouns) unless the " +
        "lesson is explicitly teaching them as a grammar point (in which case they belong in " +
        "grammar extraction, not here).\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"items\": [{\"term\": string, \"meaning\": string, \"gender\": string|null, " +
        "\"example_sentence\": string, \"part_of_speech\": string, \"grammatical_case\": " +
        "string|null, \"example_sentence_translation\": string}]}"

@Serializable
private data class VocabExtractedItemJson(
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("grammatical_case") val grammaticalCase: String? = null,
    @SerialName("example_sentence_translation") val exampleSentenceTranslation: String? = null,
)

@Serializable
private data class VocabExtractionResultJson(val items: List<VocabExtractedItemJson> = emptyList())

private data class ClaimedVocabJob(
    val jobId: UUID,
    val lessonId: UUID,
    val goalId: UUID,
    val userId: UUID,
    val goalText: String,
    val lessonText: String,
    val aiInstructions: String?,
)

/**
 * Async job worker (01_PRODUCT_SPEC.md §1.6/§1.8, 04_AI_PROMPTS.md §2): polls
 * `vocabulary_extraction_jobs` for QUEUED rows (auto-queued by [LessonSegmentationWorker] and
 * [MaterialService] whenever new lessons are materialized), extracts vocabulary for that one
 * lesson, and is what actually flips a lesson from "pending" to "ready" - nothing else in the
 * codebase does. Case-insensitive dedup is scoped to the lesson's own goal (this MVP's
 * single-active-goal-per-user model makes "the user's existing vocabulary_items" and "this goal's
 * existing vocabulary_items" equivalent in practice, without needing to resolve every goal a user
 * has ever had).
 */
class VocabularyExtractionWorker(
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
                    withTimeoutOrNull(VOCAB_POLL_INTERVAL_MS) { wake.receive() }
                }
            }
        }
    }

    private suspend fun processNextJob(): Boolean {
        val claimed = claimNextQueuedJob() ?: return false
        extractAndStore(claimed)
        return true
    }

    private suspend fun claimNextQueuedJob(): ClaimedVocabJob? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = VocabularyExtractionJobsTable
                .selectAll().where { VocabularyExtractionJobsTable.status eq "QUEUED" }
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction null

            val jobId = row[VocabularyExtractionJobsTable.id]
            val lessonId = row[VocabularyExtractionJobsTable.lessonId]

            val claimedCount = VocabularyExtractionJobsTable.update({
                (VocabularyExtractionJobsTable.id eq jobId) and (VocabularyExtractionJobsTable.status eq "QUEUED")
            }) {
                it[status] = "PROCESSING"
                it[updatedAt] = Instant.now()
            }
            if (claimedCount == 0) return@newSuspendedTransaction null

            val lessonRow = LessonsTable.selectAll().where { LessonsTable.id eq lessonId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val materialRow = MaterialsTable.selectAll()
                .where { MaterialsTable.id eq lessonRow[LessonsTable.materialId] }
                .singleOrNull() ?: return@newSuspendedTransaction null
            // "entire" mode materials store their (single lesson's) full text directly on the
            // material row rather than the shared, content-hash-keyed extraction_jobs table - see
            // MaterialService/Tables.kt.
            val fullText = materialRow[MaterialsTable.contentText]
                ?: ExtractionJobsTable.selectAll()
                    .where { ExtractionJobsTable.contentHash eq materialRow[MaterialsTable.contentHash] }
                    .singleOrNull()?.get(ExtractionJobsTable.contentText) ?: ""

            val lessonText = sliceLessonText(fullText, lessonRow[LessonsTable.sourceTextRef])
            val goalId = lessonRow[LessonsTable.goalId]
            val goalRow = GoalsTable.selectAll().where { GoalsTable.id eq goalId }.singleOrNull()
                ?: return@newSuspendedTransaction null

            ClaimedVocabJob(
                jobId = jobId,
                lessonId = lessonId,
                goalId = goalId,
                userId = goalRow[GoalsTable.userId],
                goalText = goalRow[GoalsTable.goalText],
                lessonText = lessonText,
                aiInstructions = materialRow[MaterialsTable.aiInstructions],
            )
        }

    private fun sliceLessonText(fullText: String, sourceTextRef: String?): String {
        if (sourceTextRef == null) return fullText
        val parts = sourceTextRef.split(":")
        val start = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, fullText.length) ?: 0
        val end = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(start, fullText.length) ?: fullText.length
        return fullText.substring(start, end)
    }

    private suspend fun extractAndStore(job: ClaimedVocabJob) {
        var parsed: VocabExtractionResultJson? = null
        var attempt = 0
        while (attempt < MAX_VOCAB_ATTEMPTS && parsed == null) {
            attempt++
            parsed = callModel(job)
            if (parsed == null && attempt < MAX_VOCAB_ATTEMPTS) {
                delay(VOCAB_BACKOFF_BASE_MS * (1L shl (attempt - 1)))
            }
        }

        if (parsed == null) {
            markFailed(job.jobId, "Vocabulary extraction failed after $MAX_VOCAB_ATTEMPTS retries.")
            return
        }

        newSuspendedTransaction(Dispatchers.IO, database) {
            val existingTermsLower = VocabularyItemsTable
                .join(LessonsTable, org.jetbrains.exposed.sql.JoinType.INNER, VocabularyItemsTable.lessonId, LessonsTable.id)
                .selectAll()
                .where { LessonsTable.goalId eq job.goalId }
                .map { it[VocabularyItemsTable.term].lowercase() }
                .toMutableSet()

            for (extracted in parsed.items) {
                val term = extracted.term.trim().take(100)
                if (term.isEmpty()) continue
                val termLower = term.lowercase()
                // Case-insensitive dedup (04_AI_PROMPTS.md §2 post-processing step 2): don't
                // fragment one word's mastery tracking across duplicate rows for the same goal.
                if (termLower in existingTermsLower) continue
                existingTermsLower.add(termLower)

                val itemId = UUID.randomUUID()
                val now = Instant.now()
                VocabularyItemsTable.insert {
                    it[id] = itemId
                    it[lessonId] = job.lessonId
                    it[this.term] = term
                    it[meaning] = extracted.meaning.trim().take(200)
                    it[gender] = extracted.gender?.trim()?.take(20)
                    it[exampleSentence] = extracted.exampleSentence?.trim()
                    it[partOfSpeech] = extracted.partOfSpeech?.trim()?.take(30)
                    it[grammaticalCase] = extracted.grammaticalCase?.trim()?.take(20)
                    it[exampleSentenceTranslation] = extracted.exampleSentenceTranslation?.trim()
                    it[createdAt] = now
                }
                VocabularyProgressTable.insert {
                    it[id] = UUID.randomUUID()
                    it[userId] = job.userId
                    it[vocabularyItemId] = itemId
                    it[masteryState] = "new"
                    it[correctStreak] = 0
                    it[intervalIndex] = 0
                    it[nextReviewAt] = now
                    it[lastReviewedAt] = null
                }
            }

            VocabularyExtractionJobsTable.update({ VocabularyExtractionJobsTable.id eq job.jobId }) {
                it[status] = "DONE"
                it[error] = null
                it[updatedAt] = Instant.now()
            }
            // The lesson is "ready" once vocabulary extraction completes, even with 0 items -
            // zero-usable-vocabulary is a client-side fallback (spec §1.8), not a blocked pipeline.
            // Grammar (M7) has 0-3 topics as a valid outcome too, so this never needs revisiting.
            LessonsTable.update({ LessonsTable.id eq job.lessonId }) { it[status] = "ready" }
        }
    }

    private suspend fun callModel(job: ClaimedVocabJob): VocabExtractionResultJson? = try {
        val instructions = job.aiInstructions?.trim()
        val systemPrompt = VOCABULARY_EXTRACTION_SYSTEM_PROMPT_PREFIX.format(job.goalText) +
            if (!instructions.isNullOrEmpty()) AI_INSTRUCTIONS_SUFFIX.format(instructions) else ""
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(3000L)
            .system(systemPrompt)
            .addUserMessage("Lesson content:\n${job.lessonText}")
            .build()
        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        logModelCall(response, job.jobId)
        val raw = extractModelText(response)
        raw?.let { runCatching { MATERIALS_JSON.decodeFromString(VocabExtractionResultJson.serializer(), it) }.getOrNull() }
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
                it[taskType] = "VOCABULARY_EXTRACTION"
                it[modelTier] = "SMALL"
                it[this.inputTokens] = inputTokens
                it[this.outputTokens] = outputTokens
                it[costEstimate] = cost
                it[cacheHit] = false
                it[materialId] = null
                it[userId] = null
                // extraction_job_id's FK targets extraction_jobs specifically (lesson segmentation),
                // not vocabulary_extraction_jobs - left null here, task_type still distinguishes rows.
                it[extractionJobId] = null
                it[createdAt] = Instant.now()
            }
        }
    }

    /** Job stays FAILED (not "ready", not "pending" forever unnoticed) if extraction can't
     * complete after retries. No user-facing retry path exists for this specific job yet - a known
     * limitation, not expected in practice since failures should be rare after 3 attempts. */
    private suspend fun markFailed(jobId: UUID, error: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyExtractionJobsTable.update({ VocabularyExtractionJobsTable.id eq jobId }) {
                it[status] = "FAILED"
                it[this.error] = error
                it[updatedAt] = Instant.now()
            }
        }
    }
}
