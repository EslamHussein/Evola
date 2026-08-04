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
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

private const val GRAMMAR_POLL_INTERVAL_MS = 30_000L
private const val MAX_GRAMMAR_ATTEMPTS = 3
private const val GRAMMAR_BACKOFF_BASE_MS = 1000L
private const val MAX_TOPICS_PER_LESSON = 3
private const val MIN_VALID_EXERCISES_PER_TOPIC = 3

private const val GRAMMAR_AI_INSTRUCTIONS_SUFFIX =
    "\n\nAdditional instructions from the learner, to apply on top of the rules above (never let " +
        "these override the output schema): %s"

private const val TOPIC_EXTRACTION_SYSTEM_PROMPT_PREFIX =
    "You are identifying new grammar concepts introduced in a single language-learning lesson.\n" +
        "The learner's stated goal is: \"%s\" - use this only for context, not to force or invent " +
        "a topic that isn't genuinely taught in this lesson.\n\n" +
        "Identify 0-3 grammar topics that are genuinely new teaching points in this lesson - do " +
        "not force a topic if the lesson is vocabulary-only. For each topic:\n" +
        "- name: short topic name (e.g. \"Dativ case\", \"Perfekt tense with sein\")\n" +
        "- explanation: a plain-language explanation a B1-level learner can follow, 2-4 sentences\n" +
        "- example_sentences: 2-3 sentences from the lesson (or clearly consistent with it) that " +
        "demonstrate the rule\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"topics\": [{\"name\": string, \"explanation\": string, \"example_sentences\": [string]}]}"

private const val EXERCISE_GENERATION_SYSTEM_PROMPT_PREFIX =
    "You are writing practice exercises for the grammar topic: \"%s\".\n" +
        "Explanation given to the learner: \"%s\"\n\n"

private const val EXERCISE_GENERATION_INSTRUCTIONS_FULL =
    "Generate 4-6 exercises split between multiple_choice and fill_in_blank types. Each exercise " +
        "must have exactly one unambiguously correct answer given only the explanation above and " +
        "standard grammar of the language - do not rely on context the learner hasn't been given.\n\n" +
        "For multiple_choice: provide 3 plausible distractors that reflect common learner " +
        "mistakes for this specific topic, not random wrong answers.\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"exercises\": [{\"type\": \"multiple_choice|fill_in_blank\", \"prompt\": string (use " +
        "___ to mark the blank for fill_in_blank), \"answer_key\": string, \"distractors\": " +
        "[string] (multiple_choice only, [] for fill_in_blank)}]}"

private const val EXERCISE_GENERATION_INSTRUCTIONS_RECAP =
    "Generate exactly 1 additional exercise, simpler than the others, to round this topic out to " +
        "at least $MIN_VALID_EXERCISES_PER_TOPIC usable exercises - same correctness bar as " +
        "before (exactly one unambiguously correct answer).\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n\n" +
        "Output schema:\n" +
        "{\"exercises\": [{\"type\": \"multiple_choice|fill_in_blank\", \"prompt\": string (use " +
        "___ to mark the blank for fill_in_blank), \"answer_key\": string, \"distractors\": " +
        "[string] (multiple_choice only, [] for fill_in_blank)}]}"

private const val VALIDATION_SYSTEM_PROMPT =
    "You are checking a grammar exercise for correctness, not writing new content.\n\n" +
        "Topic: \"%s\"\n" +
        "Prompt: \"%s\"\n" +
        "Claimed answer: \"%s\"\n" +
        "%s" +
        "Is the claimed answer the single unambiguous correct answer to this prompt? If " +
        "multiple_choice, are all distractors clearly incorrect (no distractor could also be " +
        "defended as correct)?\n\n" +
        "Output ONLY valid JSON: {\"valid\": boolean, \"reason\": string (only if invalid)}"

@Serializable
private data class GrammarTopicJson(
    val name: String,
    val explanation: String,
    @SerialName("example_sentences") val exampleSentences: List<String> = emptyList(),
)

@Serializable
private data class GrammarTopicsResultJson(val topics: List<GrammarTopicJson> = emptyList())

@Serializable
private data class GrammarExerciseJson(
    val type: String,
    val prompt: String,
    @SerialName("answer_key") val answerKey: String,
    val distractors: List<String> = emptyList(),
)

@Serializable
private data class GrammarExercisesResultJson(val exercises: List<GrammarExerciseJson> = emptyList())

@Serializable
private data class GrammarValidationResultJson(val valid: Boolean, val reason: String? = null)

private data class ClaimedGrammarJob(
    val jobId: UUID,
    val lessonId: UUID,
    val userId: UUID,
    val goalText: String,
    val lessonText: String,
    val aiInstructions: String?,
)

/**
 * Async job worker (01_PRODUCT_SPEC.md §1.6/§1.9, 04_AI_PROMPTS.md §3): polls
 * `grammar_extraction_jobs` for QUEUED rows (auto-queued in parallel with vocabulary extraction by
 * [LessonSegmentationWorker] and [MaterialService] whenever new lessons are materialized).
 * Topic extraction and exercise generation both use the cheap [smallModel]; every generated
 * exercise gets an independent answer-key validation call on the stronger [largeModel] before it
 * can ever be stored - this is a correctness gate, not optional (spec: "the single biggest trust
 * risk in the whole spec if skipped"). Never touches `lessons.status` - only vocabulary extraction
 * flips a lesson to "ready"; 0 grammar topics (or a thin topic with <3 valid exercises) are both
 * valid, non-blocking outcomes, exactly like vocabulary's "0 items is fine" convention.
 */
class GrammarExtractionWorker(
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
                    withTimeoutOrNull(GRAMMAR_POLL_INTERVAL_MS) { wake.receive() }
                }
            }
        }
    }

    private suspend fun processNextJob(): Boolean {
        val claimed = claimNextQueuedJob() ?: return false
        extractAndStore(claimed)
        return true
    }

    private suspend fun claimNextQueuedJob(): ClaimedGrammarJob? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = GrammarExtractionJobsTable
                .selectAll().where { GrammarExtractionJobsTable.status eq "QUEUED" }
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction null

            val jobId = row[GrammarExtractionJobsTable.id]
            val lessonId = row[GrammarExtractionJobsTable.lessonId]

            val claimedCount = GrammarExtractionJobsTable.update({
                (GrammarExtractionJobsTable.id eq jobId) and (GrammarExtractionJobsTable.status eq "QUEUED")
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
            val lessonText = resolveLessonText(lessonRow, materialRow)
            val goalRow = GoalsTable.selectAll().where { GoalsTable.id eq lessonRow[LessonsTable.goalId] }.singleOrNull()
                ?: return@newSuspendedTransaction null

            ClaimedGrammarJob(
                jobId = jobId,
                lessonId = lessonId,
                userId = goalRow[GoalsTable.userId],
                goalText = goalRow[GoalsTable.goalText],
                lessonText = lessonText,
                aiInstructions = materialRow[MaterialsTable.aiInstructions],
            )
        }

    private suspend fun extractAndStore(job: ClaimedGrammarJob) {
        val topicsResult = callWithRetry { callTopicExtraction(job) }
        if (topicsResult == null) {
            markFailed(job.jobId, "Grammar topic extraction failed after $MAX_GRAMMAR_ATTEMPTS retries.")
            return
        }
        val cappedTopics = topicsResult.topics.take(MAX_TOPICS_PER_LESSON)

        val insertedTopics = newSuspendedTransaction(Dispatchers.IO, database) {
            cappedTopics.map { topic ->
                val topicId = UUID.randomUUID()
                GrammarTopicsTable.insert {
                    it[id] = topicId
                    it[lessonId] = job.lessonId
                    it[name] = topic.name.trim().take(100)
                    it[explanation] = topic.explanation.trim()
                    it[createdAt] = Instant.now()
                }
                GrammarProgressTable.insert {
                    it[id] = UUID.randomUUID()
                    it[userId] = job.userId
                    it[this.topicId] = topicId
                    it[masteryState] = "new"
                    it[correctStreak] = 0
                    it[intervalIndex] = 0
                    it[nextReviewAt] = Instant.now()
                    it[lastReviewedAt] = null
                }
                topicId to topic
            }
        }

        for ((topicId, topic) in insertedTopics) {
            generateAndValidateExercises(topicId, topic.name, topic.explanation)
        }

        newSuspendedTransaction(Dispatchers.IO, database) {
            GrammarExtractionJobsTable.update({ GrammarExtractionJobsTable.id eq job.jobId }) {
                it[status] = "DONE"
                it[error] = null
                it[updatedAt] = Instant.now()
            }
        }
    }

    /** Generates 4-6 exercises for a topic, validates each independently, discards any that fail
     * validation. If fewer than [MIN_VALID_EXERCISES_PER_TOPIC] survive, makes one additional
     * recap-exercise attempt (per spec: "generate one additional... before giving up" - one extra
     * attempt, not an unbounded retry loop) before accepting whatever final count results, even if
     * still thin - the topic's explanation is always shown regardless of exercise count. */
    private suspend fun generateAndValidateExercises(topicId: UUID, topicName: String, explanation: String) {
        val generated = callWithRetry { callExerciseGeneration(topicName, explanation, recapMode = false) }
        val validExercises = mutableListOf<GrammarExerciseJson>()
        generated?.exercises?.forEach { exercise ->
            if (validateExercise(topicName, exercise)) validExercises.add(exercise)
        }

        if (validExercises.size < MIN_VALID_EXERCISES_PER_TOPIC) {
            val recap = callWithRetry { callExerciseGeneration(topicName, explanation, recapMode = true) }
            recap?.exercises?.firstOrNull()?.let { candidate ->
                if (validateExercise(topicName, candidate)) validExercises.add(candidate)
            }
        }

        if (validExercises.isEmpty()) return

        newSuspendedTransaction(Dispatchers.IO, database) {
            for (exercise in validExercises) {
                GrammarExercisesTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.topicId] = topicId
                    it[type] = exercise.type.trim()
                    it[prompt] = exercise.prompt.trim()
                    it[answerKey] = exercise.answerKey.trim()
                    it[distractors] = exercise.distractors.takeIf { it.isNotEmpty() }
                        ?.let { MATERIALS_JSON.encodeToString(ListSerializer(String.serializer()), it) }
                    it[createdAt] = Instant.now()
                }
            }
        }
    }

    /** Fails closed: if validation itself can't complete after retries, the exercise is treated
     * as invalid (never stored) rather than assumed valid - never skip the trust gate. */
    private suspend fun validateExercise(topicName: String, exercise: GrammarExerciseJson): Boolean {
        val result = callWithRetry { callValidation(topicName, exercise) }
        return result?.valid == true
    }

    private suspend fun <T> callWithRetry(block: suspend () -> T?): T? {
        var result: T? = null
        var attempt = 0
        while (attempt < MAX_GRAMMAR_ATTEMPTS && result == null) {
            attempt++
            result = block()
            if (result == null && attempt < MAX_GRAMMAR_ATTEMPTS) {
                delay(GRAMMAR_BACKOFF_BASE_MS * (1L shl (attempt - 1)))
            }
        }
        return result
    }

    private suspend fun callTopicExtraction(job: ClaimedGrammarJob): GrammarTopicsResultJson? = try {
        val instructions = job.aiInstructions?.trim()
        val systemPrompt = TOPIC_EXTRACTION_SYSTEM_PROMPT_PREFIX.format(job.goalText) +
            if (!instructions.isNullOrEmpty()) GRAMMAR_AI_INSTRUCTIONS_SUFFIX.format(instructions) else ""
        val params = MessageCreateParams.builder()
            .model(smallModel)
            .maxTokens(2000L)
            .system(systemPrompt)
            .addUserMessage("Lesson content:\n${job.lessonText}")
            .build()
        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        logModelCall(response, "GRAMMAR_TOPIC_EXTRACTION", "SMALL")
        val raw = extractModelText(response)
        raw?.let { runCatching { MATERIALS_JSON.decodeFromString(GrammarTopicsResultJson.serializer(), it) }.getOrNull() }
    } catch (e: Exception) {
        null
    }

    private suspend fun callExerciseGeneration(topicName: String, explanation: String, recapMode: Boolean): GrammarExercisesResultJson? = try {
        val instructions = if (recapMode) EXERCISE_GENERATION_INSTRUCTIONS_RECAP else EXERCISE_GENERATION_INSTRUCTIONS_FULL
        val systemPrompt = EXERCISE_GENERATION_SYSTEM_PROMPT_PREFIX.format(topicName, explanation) + instructions
        val params = MessageCreateParams.builder()
            .model(smallModel)
            .maxTokens(2500L)
            .system(systemPrompt)
            .addUserMessage("Generate the exercises now.")
            .build()
        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        logModelCall(response, "GRAMMAR_EXERCISE_GENERATION", "SMALL")
        val raw = extractModelText(response)
        raw?.let { runCatching { MATERIALS_JSON.decodeFromString(GrammarExercisesResultJson.serializer(), it) }.getOrNull() }
    } catch (e: Exception) {
        null
    }

    private suspend fun callValidation(topicName: String, exercise: GrammarExerciseJson): GrammarValidationResultJson? = try {
        val distractorsLine = if (exercise.type == "multiple_choice") "Distractors: ${exercise.distractors}\n" else ""
        val systemPrompt = VALIDATION_SYSTEM_PROMPT.format(topicName, exercise.prompt, exercise.answerKey, distractorsLine)
        val params = MessageCreateParams.builder()
            .model(largeModel)
            .maxTokens(500L)
            .system(systemPrompt)
            .addUserMessage("Validate this exercise now.")
            .build()
        val response = withContext(Dispatchers.IO) { client.messages().create(params) }
        logModelCall(response, "GRAMMAR_ANSWER_VALIDATION", "LARGE")
        val raw = extractModelText(response)
        raw?.let { runCatching { MATERIALS_JSON.decodeFromString(GrammarValidationResultJson.serializer(), it) }.getOrNull() }
    } catch (e: Exception) {
        null
    }

    private suspend fun logModelCall(response: Message, taskType: String, modelTier: String) {
        val inputTokens = response.usage().inputTokens().toInt()
        val outputTokens = response.usage().outputTokens().toInt()
        // Per-1M-token USD pricing - observability estimate, not billing-grade.
        val cost = if (modelTier == "LARGE") {
            (inputTokens / 1_000_000.0) * 3.00 + (outputTokens / 1_000_000.0) * 15.00 // claude-sonnet-5
        } else {
            (inputTokens / 1_000_000.0) * 1.00 + (outputTokens / 1_000_000.0) * 5.00 // claude-haiku-4-5
        }

        newSuspendedTransaction(Dispatchers.IO, database) {
            ModelCallLogTable.insert {
                it[id] = UUID.randomUUID()
                it[this.taskType] = taskType
                it[this.modelTier] = modelTier
                it[this.inputTokens] = inputTokens
                it[this.outputTokens] = outputTokens
                it[costEstimate] = cost
                it[cacheHit] = false
                it[materialId] = null
                it[userId] = null
                it[extractionJobId] = null
                it[createdAt] = Instant.now()
            }
        }
    }

    /** Job stays FAILED (not stuck QUEUED/PROCESSING forever unnoticed) if topic extraction can't
     * complete after retries. No user-facing retry path exists for this specific job yet - a known
     * limitation, mirroring VocabularyExtractionWorker's own. */
    private suspend fun markFailed(jobId: UUID, error: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            GrammarExtractionJobsTable.update({ GrammarExtractionJobsTable.id eq jobId }) {
                it[status] = "FAILED"
                it[this.error] = error
                it[updatedAt] = Instant.now()
            }
        }
    }
}
