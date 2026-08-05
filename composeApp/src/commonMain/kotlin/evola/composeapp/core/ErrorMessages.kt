package evola.composeapp.core

import evola.shared.core.DataError

/** Maps a repository [DataError] to a user-facing string. Lives in the UI layer so `:shared` stays
 * presentation-free. Prefers the server's own message when it sent one (e.g. "Email already
 * registered") over a generic status-based fallback. */
fun DataError.toUserMessage(): String = when (this) {
    DataError.Network -> "You appear to be offline. Check your connection and try again."
    DataError.Unexpected -> "Something went wrong. Please try again."
    is DataError.Http -> serverMessage ?: when (code) {
        401 -> "Your session has expired. Please sign in again."
        403 -> "You don't have access to that."
        404 -> "We couldn't find that."
        in 500..599 -> "Something went wrong on our end. Please try again in a moment."
        else -> "Something went wrong ($code)."
    }
}
