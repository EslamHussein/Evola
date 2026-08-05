package evola.composeapp.di

import evola.composeapp.SessionStorage
import evola.shared.auth.HttpAuthRepository
import evola.shared.core.TokenStore
import evola.shared.core.createApiHttpClient
import evola.shared.core.createBaseHttpClient
import evola.shared.goals.HttpGoalsRepository
import evola.shared.grammar.HttpGrammarRepository
import evola.shared.lessons.HttpLessonsRepository
import evola.shared.materials.HttpMaterialsRepository
import evola.shared.vocabulary.HttpVocabularyRepository

/**
 * The single composition root (manual DI — no Koin at this tier). Builds one JSON config, one HTTP
 * engine, and exactly two clients: a plain [base][createBaseHttpClient] one for the auth
 * token-source endpoints, and one [authenticated][createApiHttpClient] client (bearer plugin) that
 * every data repository shares. Replaces the six independently-constructed `HttpClient`s and the
 * manual token threading that used to live in `App.kt`.
 */
class AppModule(baseUrl: String, sessionStorage: SessionStorage) {

    /** Exposed so `App.kt` can save tokens on login and clear them on logout — the one place the
     * app writes the session the bearer plugin then reads and refreshes on its own. */
    val tokenStore: TokenStore = SessionTokenStore(sessionStorage)

    // One engine, shared by both clients (an injected engine isn't owned/closed by the client).
    private val engine = platformHttpEngine()

    private val baseClient = createBaseHttpClient(engine)
    val authRepository = HttpAuthRepository(baseClient, baseUrl)

    private val apiClient = createApiHttpClient(
        engine = engine,
        tokenStore = tokenStore,
        // Refresh runs on the base client (via the auth repo), so it can't be re-intercepted into a
        // refresh loop by the Auth plugin on the api client.
        refreshAccessToken = { refreshToken -> authRepository.refresh(refreshToken) },
    )

    val goalsRepository = HttpGoalsRepository(apiClient, baseUrl)
    val materialsRepository = HttpMaterialsRepository(apiClient, baseUrl)
    val vocabularyRepository = HttpVocabularyRepository(apiClient, baseUrl)
    val lessonsRepository = HttpLessonsRepository(apiClient, baseUrl)
    val grammarRepository = HttpGrammarRepository(apiClient, baseUrl)
}
