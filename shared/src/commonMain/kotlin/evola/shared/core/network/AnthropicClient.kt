package evola.shared.core.network

import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.map
import evola.shared.core.common.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Model IDs, matching the tiers the server used: SMALL for extraction/generation, LARGE for the
 * mandatory grammar answer-key validation. */
object AnthropicModels {
    const val SMALL = "claude-haiku-4-5"
    const val LARGE = "claude-sonnet-5"
}

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicCacheControl(val type: String = "ephemeral")

/** The system prompt as a content-block array (rather than a bare string) is what makes
 * [AnthropicCacheControl] possible: caching is opted into per-block, not per-request. Every caller
 * here sends the same system text on every call within one material's processing run (the segments
 * of one document share the same lesson-extraction prompt, differing only in the user turn), so
 * this is close to a guaranteed cache hit within Anthropic's 5-minute TTL - ~90% off the input-token
 * cost of that repeated prompt from the second call onward. */
@Serializable
private data class AnthropicSystemBlock(
    val type: String = "text",
    val text: String,
    @SerialName("cache_control") val cacheControl: AnthropicCacheControl = AnthropicCacheControl(),
)

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<AnthropicSystemBlock>? = null,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicContentBlock(val type: String, val text: String? = null)

@Serializable
private data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    val usage: AnthropicUsage? = null,
)

@Serializable
private data class AnthropicImageSource(val type: String = "base64", @SerialName("media_type") val mediaType: String, val data: String)

@Serializable
private data class AnthropicRequestBlock(val type: String, val text: String? = null, val source: AnthropicImageSource? = null)

@Serializable
private data class AnthropicVisionMessage(val role: String, val content: List<AnthropicRequestBlock>)

@Serializable
private data class AnthropicVisionRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<AnthropicSystemBlock>? = null,
    val messages: List<AnthropicVisionMessage>,
)

private const val LOG_BODY_CHAR_LIMIT = 2000

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
    // encodeDefaults = true: AnthropicSystemBlock.type/AnthropicCacheControl.type/
    // AnthropicImageSource.type are all protocol-required fields that happen to have a Kotlin
    // default value - encodeDefaults=false was silently dropping them from every request (the
    // exact trap this project's kmp-ktor skill calls out), which is what made every vocab
    // extraction call fail outright once system became a content-block array with cache_control.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(Logging) {
            logger = object : KtorLogger {
                // BODY logging includes the full request/response body - for this app that's the
                // user's own uploaded study material and AI-generated lesson content, which
                // shouldn't sit verbatim in a persistent on-device log file indefinitely. Truncated
                // rather than dropped entirely, so a real failure is still diagnosable from the log.
                override fun log(message: String) {
                    val truncated = if (message.length > LOG_BODY_CHAR_LIMIT) {
                        "${message.take(LOG_BODY_CHAR_LIMIT)}... [truncated, ${message.length} chars total]"
                    } else {
                        message
                    }
                    EvolaLog.d("http", truncated)
                }
            }
            // BODY (not HEADERS/ALL) so the `x-api-key` header never lands in the log file.
            level = LogLevel.BODY
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }
    }

    /** Returns the concatenated text of the response's content blocks, or a [DataError]. A missing
     * key short-circuits to `Http(401)` so the UI can prompt the user to set it. [onUsage], when
     * given, is invoked with the call's input/output token counts on success - lets callers track
     * spend (e.g. per-material totals) without changing this function's return type. */
    suspend fun complete(
        model: String,
        maxTokens: Int,
        system: String?,
        userMessage: String,
        onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null,
    ): ApiResult<String> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
        if (key == null) {
            EvolaLog.d("anthropic", "no API key available (null/blank) — short-circuiting to 401")
            return ApiResult.Failure(DataError.Http(401, "No Anthropic API key set. Add it in Profile."))
        }
        val result = safeRequest<AnthropicResponse> {
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(AnthropicRequest(model, maxTokens, system?.let { listOf(AnthropicSystemBlock(text = it)) }, listOf(AnthropicMessage("user", userMessage))))
            }
        }
        if (result is ApiResult.Failure) {
            EvolaLog.d("anthropic", "call failed: model=$model keyLen=${key.length} inputChars=${userMessage.length} error=${result.error}")
        }
        if (result is ApiResult.Success) {
            result.data.usage?.let { onUsage?.invoke(it.inputTokens, it.outputTokens) }
        }
        return result.map { response -> response.content.mapNotNull { it.text }.joinToString("") }
    }

    /** Same contract as [complete], but the user turn is an image + text prompt instead of plain
     * text - used for the Add Resource "Image" material type (transcribing a photographed page
     * on-device, no native OCR library needed since Claude's vision input already does this). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun completeWithImage(
        model: String,
        maxTokens: Int,
        system: String?,
        prompt: String,
        imageBytes: ByteArray,
        imageMediaType: String,
        onUsage: ((inputTokens: Int, outputTokens: Int) -> Unit)? = null,
    ): ApiResult<String> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() }
        if (key == null) {
            EvolaLog.d("anthropic", "no API key available (null/blank) — short-circuiting to 401")
            return ApiResult.Failure(DataError.Http(401, "No Anthropic API key set. Add it in Profile."))
        }
        val content = listOf(
            AnthropicRequestBlock(type = "image", source = AnthropicImageSource(mediaType = imageMediaType, data = Base64.encode(imageBytes))),
            AnthropicRequestBlock(type = "text", text = prompt),
        )
        val result = safeRequest<AnthropicResponse> {
            client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", key)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(AnthropicVisionRequest(model, maxTokens, system?.let { listOf(AnthropicSystemBlock(text = it)) }, listOf(AnthropicVisionMessage("user", content))))
            }
        }
        if (result is ApiResult.Failure) {
            EvolaLog.d("anthropic", "vision call failed: model=$model keyLen=${key.length} imageBytes=${imageBytes.size} error=${result.error}")
        }
        if (result is ApiResult.Success) {
            result.data.usage?.let { onUsage?.invoke(it.inputTokens, it.outputTokens) }
        }
        return result.map { response -> response.content.mapNotNull { it.text }.joinToString("") }
    }
}
