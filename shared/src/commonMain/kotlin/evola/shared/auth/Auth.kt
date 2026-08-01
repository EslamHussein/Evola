package evola.shared.auth

data class AuthUser(val id: String, val fullName: String, val email: String, val onboardingCompleted: Boolean)

data class AuthTokens(val accessToken: String, val refreshToken: String, val user: AuthUser)

sealed interface AuthResult {
    data class Success(val tokens: AuthTokens) : AuthResult
    data object EmailTaken : AuthResult
    data object InvalidCredentials : AuthResult
    data class AccountLocked(val minutesRemaining: Long) : AuthResult
    data class ValidationError(val message: String) : AuthResult
}

sealed interface PasswordResetConfirmResult {
    data object Success : PasswordResetConfirmResult
    data object TokenInvalidOrExpired : PasswordResetConfirmResult
    data class ValidationError(val message: String) : PasswordResetConfirmResult
}

interface AuthRepository {
    suspend fun register(fullName: String, email: String, password: String): AuthResult
    suspend fun login(email: String, password: String): AuthResult
    suspend fun requestPasswordReset(email: String)
    suspend fun confirmPasswordReset(token: String, newPassword: String): PasswordResetConfirmResult
    suspend fun logout(refreshToken: String)
}
