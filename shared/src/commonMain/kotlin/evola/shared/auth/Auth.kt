package evola.shared.auth

data class SignUpRequest(val email: String, val password: String)

sealed interface SignUpResult {
    data class Success(val userId: String) : SignUpResult
    data object EmailAlreadyTaken : SignUpResult
}

interface AuthRepository {
    suspend fun signUp(request: SignUpRequest): SignUpResult
}
