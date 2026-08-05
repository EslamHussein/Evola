package evola.shared.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** The one shared JSON config. `encodeDefaults = true` is deliberate (kmp-ktor skill): with the
 * default `false`, any field whose value equals its declared default is stripped from the request
 * body — a protocol-constant like a fixed `type`/`version` silently vanishes and the server rejects
 * the call for a reason invisible at the HTTP layer. */
fun evolaJson(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** A plain client with no auth — used by [evola.shared.auth] for its token-source endpoints
 * (login/register/refresh/logout), and as the base the api client below refreshes through, so a
 * refresh POST can never be re-intercepted by the [Auth] plugin (the loop the kmp-ktor skill warns
 * about). `expectSuccess = false`: status is inspected in [safeRequest], never thrown by Ktor. */
fun createBaseHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(evolaJson()) }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

/**
 * The authenticated client every *data* repository shares. The [Auth] bearer plugin attaches
 * `Authorization: Bearer …` on every request and, on a 401, refreshes once via
 * [refreshAccessToken] (which runs on the base client, so it's never re-intercepted) — this is what
 * lets the access token drop out of every data-repository and ViewModel signature.
 *
 * Plugin order matters (kmp-ktor skill): [HttpRequestRetry] is installed BEFORE [HttpTimeout] so a
 * timeout is seen by retry rather than resolving the request as failed first.
 */
fun createApiHttpClient(
    engine: HttpClientEngine,
    tokenStore: TokenStore,
    refreshAccessToken: suspend (refreshToken: String) -> String?,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(evolaJson()) }
    install(Auth) {
        bearer {
            loadTokens { tokenStore.get()?.let { BearerTokens(it.access, it.refresh) } }
            refreshTokens {
                val current = tokenStore.get() ?: return@refreshTokens null
                val newAccess = refreshAccessToken(current.refresh)
                if (newAccess == null) {
                    tokenStore.clear()
                    return@refreshTokens null
                }
                val updated = Tokens(access = newAccess, refresh = current.refresh)
                tokenStore.save(updated)
                BearerTokens(updated.access, updated.refresh)
            }
            // Every data endpoint requires auth, so send the token proactively rather than paying a
            // 401-then-retry round-trip on each call.
            sendWithoutRequest { true }
        }
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
