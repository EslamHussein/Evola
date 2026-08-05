package evola.shared.core

/**
 * The domain error type every repository maps failures to at its boundary (android-data-layer
 * skill): platform/HTTP exceptions never leak past a repository into a ViewModel. Deliberately
 * presentation-free — turning a [DataError] into a user-facing string is the UI layer's job, so
 * `:shared` carries no copy.
 */
sealed interface DataError {
    /** Offline, DNS failure, connection dropped, or a request/socket timeout. */
    data object Network : DataError

    /** A non-2xx response. [serverMessage] is the server's own `error.message` when present, so the
     * UI can surface a real reason ("Email already registered") instead of a generic one. */
    data class Http(val code: Int, val serverMessage: String?) : DataError

    /** Serialization failure or any other unexpected throwable. */
    data object Unexpected : DataError
}

/** Result of a repository call: the value on success, or a mapped [DataError] on failure. Replaces
 * the old convention of returning `null`/`emptyList()` on every failure, which collapsed "offline",
 * "404", "500", and "bad JSON" into one indistinguishable signal. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: DataError) : ApiResult<Nothing>
}

/** The value on success, or null on failure — for call sites that don't care *why* it failed. */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/** Transforms the success value (e.g. wire DTO → domain type), leaving a failure untouched — lets a
 * repository write `safeRequest<Wire> { … }.map { it.toDomain() }`. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

inline fun <T, R> ApiResult<T>.fold(onSuccess: (T) -> R, onFailure: (DataError) -> R): R = when (this) {
    is ApiResult.Success -> onSuccess(data)
    is ApiResult.Failure -> onFailure(error)
}
