package evola.composeapp

import androidx.compose.runtime.Composable

/** Persists the refresh token across app restarts so the user isn't asked to log in every time. */
interface SessionStorage {
    fun saveRefreshToken(token: String)
    fun loadRefreshToken(): String?
    fun clear()
}

@Composable
expect fun rememberSessionStorage(): SessionStorage
