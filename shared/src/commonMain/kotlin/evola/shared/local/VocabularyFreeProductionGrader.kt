package evola.shared.local

import evola.shared.ai.AnthropicClient
import evola.shared.ai.AnthropicModels
import evola.shared.core.ApiResult
import kotlinx.serialization.Serializable

/** Stage 6 (Free Production) grading result: whether the learner's original sentence passes, plus
 * one short encouraging note. */
data class VocabGradingResult(val passable: Boolean, val feedback: String?)

/** Seam so [LocalVocabularyRepository] doesn't depend on a concrete grader — tests substitute a
 * trivial fake, exactly like the retired server's `VocabularyGrader`. */
fun interface VocabularyFreeProductionGrader {
    suspend fun grade(term: String, userSentence: String): VocabGradingResult
}

@Serializable
private data class FreeProductionGradingJson(val passable: Boolean, val feedback: String)

/** On-device AI grading for Stage 6 — one direct Anthropic (haiku) call, failing open to a pass so
 * a network/key hiccup never blocks pack completion. Ports the server's `FreeProductionGrader`. */
class AiVocabularyFreeProductionGrader(private val client: AnthropicClient) : VocabularyFreeProductionGrader {
    override suspend fun grade(term: String, userSentence: String): VocabGradingResult {
        val fallback = VocabGradingResult(true, "Nice work — we couldn't check this one, but keep practicing!")
        val system = SYSTEM_PROMPT_PREFIX + term + SYSTEM_PROMPT_SUFFIX
        val raw = when (val r = client.complete(AnthropicModels.SMALL, 250, system, userSentence)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> return fallback
        }
        val parsed = runCatching {
            localJson.decodeFromString(FreeProductionGradingJson.serializer(), raw.trim().removeSurrounding("```json", "```").trim())
        }.getOrNull() ?: runCatching {
            localJson.decodeFromString(FreeProductionGradingJson.serializer(), raw)
        }.getOrNull() ?: return fallback
        return VocabGradingResult(parsed.passable, parsed.feedback)
    }

    private companion object {
        const val SYSTEM_PROMPT_PREFIX =
            "You are grading a German-language learner's original sentence for a vocabulary drill.\n" +
                "The learner was asked to write an original German sentence using the word \""
        const val SYSTEM_PROMPT_SUFFIX = "\".\n" +
            "Judge grammar and correct usage of the word, not stylistic sophistication - a short, " +
            "simple, grammatically correct sentence using the word appropriately should pass.\n\n" +
            "Output ONLY valid JSON, no prose, no markdown fences.\n" +
            "Output schema: {\"passable\": boolean, \"feedback\": string}\n" +
            "feedback: one short, encouraging sentence (in English) explaining what was right or what " +
            "to fix - 1-2 sentences max."
    }
}
