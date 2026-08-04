package evola.shared.core

/** The access/refresh pair the Ktor `Auth` bearer plugin loads and refreshes. */
data class Tokens(val access: String, val refresh: String)

/**
 * Bridges the Ktor `Auth` bearer plugin to wherever the app actually persists its session. The
 * plugin's `loadTokens`/`refreshTokens` read and write through this, so `Authorization: Bearer …`
 * and 401-triggered refresh happen centrally — no repository or ViewModel handles the access token
 * anymore. The implementation ([evola.composeapp] side) is backed by the existing session storage.
 */
interface TokenStore {
    fun get(): Tokens?
    fun save(tokens: Tokens)
    fun clear()
}
