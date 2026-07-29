package evola.integrations.aigateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"

private const val SYSTEM_PROMPT = "You are a concise German language tutor. Respond with exactly one short " +
    "German example sentence that naturally uses the given word. No translation, no quotes, no explanation."

/**
 * OpenAI-backed [AiTutorPort]. The model is injected (see [DEFAULT_EXERCISE_MODEL] / the
 * OPENAI_EXERCISE_MODEL env var wired in composition-root) so swapping tiers per operation is a
 * config change, not a code change — the concrete cost-tiering mechanism from ADR-0001-mvp.
 */
class OpenAiAiTutorPort(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val exerciseModel: String,
) : AiTutorPort {

    override suspend fun generateExercise(request: GenerateExerciseRequest): GeneratedExercise {
        val httpResponse = httpClient.post(OPENAI_CHAT_COMPLETIONS_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequest(
                    model = exerciseModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = SYSTEM_PROMPT),
                        ChatMessage(role = "user", content = buildPrompt(request)),
                    ),
                ),
            )
        }
        val rawBody = httpResponse.bodyAsText()

        if (!httpResponse.status.isSuccess()) {
            error("OpenAI API error (${httpResponse.status}) for word '${request.germanWord}': $rawBody")
        }

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(ChatCompletionResponse.serializer(), rawBody)
        val content = parsed.choices.firstOrNull()?.message?.content?.trim()
            ?: error("OpenAI returned no choices for word '${request.germanWord}': $rawBody")

        return GeneratedExercise(content = content, modelUsed = exerciseModel)
    }

    private fun buildPrompt(request: GenerateExerciseRequest): String =
        "German word: \"${request.germanWord}\" (English: \"${request.englishTranslation}\"), " +
            "CEFR level ${request.cefrLevel}."

    companion object {
        /**
         * Cheapest current small-tier OpenAI chat model — verify the exact model id against
         * OpenAI's pricing page at deploy time, pricing tiers shift. Always overridable via the
         * OPENAI_EXERCISE_MODEL env var; never hardcode this into call sites.
         */
        const val DEFAULT_EXERCISE_MODEL = "gpt-5-nano"

        fun create(apiKey: String, exerciseModel: String = DEFAULT_EXERCISE_MODEL): OpenAiAiTutorPort {
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
            return OpenAiAiTutorPort(client, apiKey, exerciseModel)
        }
    }
}
