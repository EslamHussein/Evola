package evola.integrations.aigateway

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SYSTEM_PROMPT = "You are a concise German language tutor. Respond with exactly one short " +
    "German example sentence that naturally uses the given word. No translation, no quotes, no explanation."

/**
 * Anthropic-backed [AiTutorPort]. Uses Claude Haiku by default — cheap and more than sufficient
 * for a one-sentence example given a known word/translation/CEFR level (see ADR-0001-mvp cost
 * tiering: reserve a stronger model only for contexts that genuinely need deeper reasoning).
 */
class AnthropicAiTutorPort(
    private val client: AnthropicClient,
    private val exerciseModel: String,
) : AiTutorPort {

    override suspend fun generateExercise(request: GenerateExerciseRequest): GeneratedExercise =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(exerciseModel)
                .maxTokens(300L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildPrompt(request))
                .build()

            val response = client.messages().create(params)

            val content = response.content()
                .asSequence()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .firstOrNull()
                ?.trim()
                ?: error("Anthropic returned no text content for word '${request.germanWord}'")

            GeneratedExercise(content = content, modelUsed = exerciseModel)
        }

    private fun buildPrompt(request: GenerateExerciseRequest): String =
        "German word: \"${request.germanWord}\" (English: \"${request.englishTranslation}\"), " +
            "CEFR level ${request.cefrLevel}."

    companion object {
        /** Cheapest current Claude tier — verify against Anthropic's pricing page at deploy time. */
        const val DEFAULT_EXERCISE_MODEL = "claude-haiku-4-5"

        fun create(apiKey: String, exerciseModel: String = DEFAULT_EXERCISE_MODEL): AnthropicAiTutorPort {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            return AnthropicAiTutorPort(client, exerciseModel)
        }
    }
}
