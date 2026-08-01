package evola.server

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import evola.shared.materials.Exercise
import evola.shared.materials.GrammarRule
import evola.shared.materials.VocabItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val MIN_EXTRACTABLE_LENGTH = 20
private const val POLL_INTERVAL_MS = 30_000L

private const val EXTRACTION_SYSTEM_PROMPT =
    "You are a language-learning assistant extracting study material from a piece of uploaded text. " +
        "Identify vocabulary worth learning, any grammar rules the text demonstrates, and 1-3 short practice " +
        "exercises based on the content. Respond with ONLY a single valid JSON object matching this schema, " +
        "no markdown fences, no prose: {\"vocabulary\": [{\"term\": string, \"translation\": string, " +
        "\"exampleSentence\": string|null}], \"grammar\": [{\"topic\": string, \"explanation\": string}], " +
        "\"exercises\": [{\"kind\": string, \"prompt\": string, \"answerKey\": string}]}. " +
        "If the text has no extractable vocabulary/grammar/exercises, return empty arrays for those fields."

/** Rough per-1M-token USD pricing for the observability estimate — not billing-grade precision. */
private val PRICING: Map<String, Pair<Double, Double>> = mapOf(
    "claude-haiku-4-5" to (1.00 to 5.00),
    "claude-sonnet-5" to (3.00 to 15.00),
)

@Serializable
private data class ExtractionResultJson(
    val vocabulary: List<VocabItem> = emptyList(),
    val grammar: List<GrammarRule> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
) {
    val isEmpty: Boolean get() = vocabulary.isEmpty() && grammar.isEmpty() && exercises.isEmpty()
}

private data class AttemptResult(val parsed: ExtractionResultJson?, val model: String)

private data class ClaimedJob(val id: UUID, val contentHash: String, val contentText: String)

/**
 * Async job worker (spec §5.2): polls `extraction_jobs` for QUEUED rows (woken immediately via
 * [wake] after a new upload, plus a periodic fallback poll for jobs left over a restart), runs the
 * model router (SMALL tier first, escalate to LARGE only if SMALL fails to parse or returns
 * nothing), and writes the result to `extraction_cache` — this and [logModelCall] are the only
 * places in the codebase allowed to touch `model_call_log` (non-goals discipline, spec §4).
 */
class ExtractionWorker(
    private val database: Database,
    private val client: AnthropicClient,
    private val smallModel: String = "claude-haiku-4-5",
    private val largeModel: String = "claude-sonnet-5",
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
        try {
            extractAndStore(claimed)
        } catch (e: Exception) {
            markFailed(claimed, e.message ?: "Unknown extraction error")
        }
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

            val claimedCount = ExtractionJobsTable.update({
                (ExtractionJobsTable.id eq jobId) and (ExtractionJobsTable.status eq "QUEUED")
            }) {
                it[status] = "PROCESSING"
                it[updatedAt] = Instant.now()
            }
            if (claimedCount == 0) return@newSuspendedTransaction null

            MaterialsTable.update({ MaterialsTable.contentHash eq contentHash }) {
                it[status] = "ANALYZING"
            }

            ClaimedJob(jobId, contentHash, row[ExtractionJobsTable.contentText])
        }

    private suspend fun extractAndStore(job: ClaimedJob) {
        val trimmed = job.contentText.trim()
        if (trimmed.length < MIN_EXTRACTABLE_LENGTH) {
            markFailed(job, "Content too short to extract from (${trimmed.length} chars).")
            return
        }

        val firstAttempt = callModel(smallModel, trimmed, job)
        val finalAttempt = if (firstAttempt.parsed == null || firstAttempt.parsed.isEmpty) {
            callModel(largeModel, trimmed, job)
        } else {
            firstAttempt
        }

        val result = finalAttempt.parsed
        if (result == null) {
            markFailed(job, "Extraction failed on both model tiers.")
            return
        }

        val confidence = if (finalAttempt.model == smallModel) 1.0f else 0.7f
        storeResult(job, result, confidence, finalAttempt.model)
    }

    private suspend fun callModel(model: String, text: String, job: ClaimedJob): AttemptResult {
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(2000L)
            .system(EXTRACTION_SYSTEM_PROMPT)
            .addUserMessage("Content:\n$text")
            .build()

        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        val raw = extractText(response)
        val parsed = raw?.let {
            runCatching { MATERIALS_JSON.decodeFromString(ExtractionResultJson.serializer(), it) }.getOrNull()
        }

        logModelCall(model, response, job)
        return AttemptResult(parsed, model)
    }

    private suspend fun logModelCall(model: String, response: Message, job: ClaimedJob) {
        val (inPrice, outPrice) = PRICING[model] ?: (0.0 to 0.0)
        val inputTokens = response.usage().inputTokens().toInt()
        val outputTokens = response.usage().outputTokens().toInt()
        val cost = (inputTokens / 1_000_000.0) * inPrice + (outputTokens / 1_000_000.0) * outPrice

        newSuspendedTransaction(Dispatchers.IO, database) {
            ModelCallLogTable.insert {
                it[id] = UUID.randomUUID()
                it[taskType] = "EXTRACTION"
                it[modelTier] = if (model == smallModel) "SMALL" else "LARGE"
                it[this.inputTokens] = inputTokens
                it[this.outputTokens] = outputTokens
                it[costEstimate] = cost
                it[cacheHit] = false
                it[materialId] = null
                it[userId] = null
                it[extractionJobId] = job.id
                it[createdAt] = Instant.now()
            }
        }
    }

    private suspend fun storeResult(job: ClaimedJob, result: ExtractionResultJson, confidence: Float, model: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val existingCacheId = ExtractionCacheTable
                .selectAll().where { ExtractionCacheTable.contentHash eq job.contentHash }
                .singleOrNull()?.get(ExtractionCacheTable.id)

            val cacheId = existingCacheId ?: UUID.randomUUID().also { newId ->
                ExtractionCacheTable.insert {
                    it[id] = newId
                    it[contentHash] = job.contentHash
                    it[vocabulary] = MATERIALS_JSON.encodeToString(ListSerializer(VocabItem.serializer()), result.vocabulary)
                    it[grammar] = MATERIALS_JSON.encodeToString(ListSerializer(GrammarRule.serializer()), result.grammar)
                    it[exercises] = MATERIALS_JSON.encodeToString(ListSerializer(Exercise.serializer()), result.exercises)
                    it[this.confidence] = confidence
                    it[modelVersion] = model
                    it[createdAt] = Instant.now()
                }
            }

            MaterialsTable.update({ MaterialsTable.contentHash eq job.contentHash }) {
                it[status] = "ANALYZED"
                it[extractionCacheId] = cacheId
            }
            ExtractionJobsTable.update({ ExtractionJobsTable.id eq job.id }) {
                it[status] = "DONE"
                it[updatedAt] = Instant.now()
            }
        }
    }

    private suspend fun markFailed(job: ClaimedJob, error: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            ExtractionJobsTable.update({ ExtractionJobsTable.id eq job.id }) {
                it[status] = "FAILED"
                it[this.error] = error
                it[updatedAt] = Instant.now()
            }
            MaterialsTable.update({ MaterialsTable.contentHash eq job.contentHash }) {
                it[status] = "FAILED"
            }
        }
    }

    private fun extractText(response: Message): String? =
        response.content()
            .asSequence()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .firstOrNull()
            ?.trim()
}
