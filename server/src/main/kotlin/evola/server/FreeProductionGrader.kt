package evola.server

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private const val FREE_PRODUCTION_SYSTEM_PROMPT =
    "You are grading a German-language learner's original sentence for a vocabulary drill.\n" +
        "The learner was asked to write an original German sentence using the word \"%s\".\n" +
        "Judge grammar and correct usage of the word, not stylistic sophistication - a short, " +
        "simple, grammatically correct sentence using the word appropriately should pass.\n\n" +
        "Output ONLY valid JSON, no prose, no markdown fences.\n" +
        "Output schema: {\"passable\": boolean, \"feedback\": string}\n" +
        "feedback: one short, encouraging sentence (in English) explaining what was right or what " +
        "to fix - 1-2 sentences max."

@Serializable
private data class FreeProductionGradingJson(val passable: Boolean, val feedback: String)

data class GradingResult(val passable: Boolean, val feedback: String)

/** Seam so [VocabularyService] doesn't depend on the concrete Anthropic-backed grader directly -
 * unit tests substitute a trivial fake, the same way the batch extraction workers have no unit
 * tests at all (verified only via curl/real calls) because they have no such seam. */
interface VocabularyGrader {
    suspend fun grade(term: String, userSentence: String): GradingResult
}

/**
 * AI grading for the pack/stage session's Stage 6 "Free Production" (design handoff Phase 7) -
 * genuinely open-ended, unlike Stages 2-5's deterministic matching, so this is the one stage that
 * needs a real model call. Flagged in the plan as a new, unbounded-cost feature (fires once per
 * word per user per pack completion, unlike the batch extraction workers whose cost scales with
 * material/lesson count) - logged via [ModelCallLogTable] from day one so real cost stays visible;
 * shipped without a hard rate limit initially since usage is low pre-launch.
 */
class FreeProductionGrader(
    private val database: Database,
    private val client: AnthropicClient,
    private val model: String = "claude-haiku-4-5",
) : VocabularyGrader {
    override suspend fun grade(term: String, userSentence: String): GradingResult {
        val fallback = GradingResult(passable = true, feedback = "Nice work - we couldn't check this one, but keep practicing!")
        return try {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(250L)
                .system(FREE_PRODUCTION_SYSTEM_PROMPT.format(term))
                .addUserMessage(userSentence)
                .build()
            val response = withContext(Dispatchers.IO) { client.messages().create(params) }
            logModelCall(response)
            val raw = extractModelText(response) ?: return fallback
            val parsed = runCatching {
                MATERIALS_JSON.decodeFromString(FreeProductionGradingJson.serializer(), raw)
            }.getOrNull() ?: return fallback
            GradingResult(parsed.passable, parsed.feedback)
        } catch (e: Exception) {
            fallback
        }
    }

    private suspend fun logModelCall(response: Message) {
        val inputTokens = response.usage().inputTokens().toInt()
        val outputTokens = response.usage().outputTokens().toInt()
        // claude-haiku-4-5 pricing, per-1M-token USD - observability estimate, not billing-grade.
        val cost = (inputTokens / 1_000_000.0) * 1.00 + (outputTokens / 1_000_000.0) * 5.00

        newSuspendedTransaction(Dispatchers.IO, database) {
            ModelCallLogTable.insert {
                it[id] = UUID.randomUUID()
                it[taskType] = "FREE_PRODUCTION_GRADING"
                it[modelTier] = "SMALL"
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
}
