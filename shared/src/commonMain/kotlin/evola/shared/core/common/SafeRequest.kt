package evola.shared.core.common

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The single boundary every repository call routes through: run the Ktor request, and turn its
 * outcome into an [ApiResult] so no raw platform/HTTP failure ever reaches a ViewModel
 * (android-data-layer skill). Error-mapping follows the kmp-ktor rule — catch *specific* types and
 * rethrow [CancellationException] first, so a cancelled coroutine is never misreported as an error.
 *
 * Written against `expectSuccess = false` (the client inspects status here rather than letting Ktor
 * throw), matching how the repositories already worked.
 */
suspend inline fun <reified T> safeRequest(call: () -> HttpResponse): ApiResult<T> = try {
    val response = call()
    if (response.status.isSuccess()) {
        ApiResult.Success(response.body())
    } else {
        ApiResult.Failure(DataError.Http(response.status.value, response.serverMessageOrNull()))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: HttpRequestTimeoutException) {
    ApiResult.Failure(DataError.Network)
} catch (e: IOException) {
    // commonMain IOException is kotlinx-io (offline, DNS, dropped connection) — NOT java.io.
    ApiResult.Failure(DataError.Network)
} catch (e: Exception) {
    ApiResult.Failure(DataError.Unexpected)
}

private val errorJson = Json { ignoreUnknownKeys = true }

/** Pulls the server's own error text out of a failed response, tolerating both shapes the API
 * uses: `{"error":{"code","message"}}` and the plainer `{"error":"..."}`. Null when neither
 * matches — the UI then falls back to a status-code-based message. The response body is a
 * one-shot channel, so it's read exactly once here. */
suspend fun HttpResponse.serverMessageOrNull(): String? = runCatching {
    val error = errorJson.parseToJsonElement(bodyAsText()).jsonObject["error"] ?: return null
    runCatching { error.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
        ?: runCatching { error.jsonPrimitive.content }.getOrNull()
}.getOrNull()
