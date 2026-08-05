package evola.shared.ai

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Model IDs, matching the tiers the server used: SMALL for extraction/generation, LARGE for the
 * mandatory grammar answer-key validation. */
object AnthropicModels {
    const val SMALL = "claude-haiku-4-5"
    const val LARGE = "claude-sonnet-5"
}

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicContentBlock(val type: String, val text: String? = null)

@Serializable
private data class AnthropicResponse(val content: List<AnthropicContentBlock> = emptyList())

/**
 * On-device Anthropic Messages client (serverless architecture): calls `api.anthropic.com` directly
 * with the user's own key from [apiKeyProvider] (device secure store). Raw Ktor rather than the
 * JVM-only official SDK the server used, so it works on iOS too. Long timeout — extraction calls are
 * slow. Errors map through [safeRequest] to [DataError] (a 401 = bad/missing key surfaces as
 * `Http(401)`).
 */
class AnthropicClient(
    engine: HttpClientEngine,
    private val apiKeyProvider: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    /** Returns the concatenated text of the response's content blocks, or a [DataError]. A missing
     * key short-circuits to `Http(401)` so the UI can prompt the user to set it. */
    suspend fun complete(model: String, maxTokens: Int, system: String?, userMessage: String): ApiResult<String> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return ApiResult.Failure(DataError.Http(401, "No Anthropic API key set. Add it in Profile."))
        return safeRequest<AnthropicResponse> {
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(AnthropicRequest(model, maxTokens, system, listOf(AnthropicMessage("user", userMessage))))
            }
        }.map { response -> response.content.mapNotNull { it.text }.joinToString("") }
    }
}
