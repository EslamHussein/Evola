package evola.core.kernel

sealed interface DomainResult<out T> {
    data class Ok<T>(val value: T) : DomainResult<T>
    data class Err(val error: DomainError) : DomainResult<Nothing>
}

sealed class DomainError(open val message: String) {
    data class NotFound(override val message: String) : DomainError(message)
    data class ValidationFailed(override val message: String) : DomainError(message)
    data class Conflict(override val message: String) : DomainError(message)
}

inline fun <T, R> DomainResult<T>.map(transform: (T) -> R): DomainResult<R> = when (this) {
    is DomainResult.Ok -> DomainResult.Ok(transform(value))
    is DomainResult.Err -> this
}
