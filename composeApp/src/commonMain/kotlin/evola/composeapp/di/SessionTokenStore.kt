package evola.composeapp.di

import evola.composeapp.SessionStorage
import evola.shared.core.TokenStore
import evola.shared.core.Tokens

/**
 * Backs the shared [TokenStore] the Ktor `Auth` plugin reads and refreshes through. The refresh
 * token is durable (encrypted [SessionStorage], survives restarts); the access token is in-memory
 * only (short-lived, re-derived by the plugin's refresh on the first 401 after a cold start). A
 * missing refresh token means "no session" — [get] returns null and the plugin sends no bearer.
 */
class SessionTokenStore(private val sessionStorage: SessionStorage) : TokenStore {

    private var accessToken: String? = null

    override fun get(): Tokens? {
        val refresh = sessionStorage.loadRefreshToken() ?: return null
        return Tokens(access = accessToken.orEmpty(), refresh = refresh)
    }

    override fun save(tokens: Tokens) {
        accessToken = tokens.access
        sessionStorage.saveRefreshToken(tokens.refresh)
    }

    override fun clear() {
        accessToken = null
        sessionStorage.clear()
    }
}
