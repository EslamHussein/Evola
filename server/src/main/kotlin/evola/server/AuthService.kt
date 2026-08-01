package evola.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Serializable
data class RegisterRequest(@SerialName("full_name") val fullName: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class PasswordResetRequestPayload(val email: String)

@Serializable
data class PasswordResetConfirmPayload(val token: String, @SerialName("new_password") val newPassword: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UserResponse(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
)

@Serializable
data class AuthTokensResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: UserResponse,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    @SerialName("minutes_remaining") val minutesRemaining: Long? = null,
)

@Serializable
data class ErrorResponse(val error: ErrorBody)

sealed interface RegisterOutcome {
    data class Created(val tokens: AuthTokensResponse) : RegisterOutcome
    data object EmailTaken : RegisterOutcome
    data class Invalid(val message: String) : RegisterOutcome
}

sealed interface LoginOutcome {
    data class Success(val tokens: AuthTokensResponse) : LoginOutcome
    data object InvalidCredentials : LoginOutcome
    data class Locked(val minutesRemaining: Long) : LoginOutcome
}

sealed interface PasswordResetConfirmOutcome {
    data object Success : PasswordResetConfirmOutcome
    data object TokenInvalidOrExpired : PasswordResetConfirmOutcome
    data class Invalid(val message: String) : PasswordResetConfirmOutcome
}

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val FULL_NAME_PATTERN = Regex("^[\\p{L} '-]{2,60}$")
private const val LOCKOUT_THRESHOLD = 5
private val LOCKOUT_WINDOW: Duration = Duration.ofMinutes(15)
private val LOCKOUT_DURATION: Duration = Duration.ofMinutes(15)
private val ACCESS_TOKEN_TTL: Duration = Duration.ofHours(24)
private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
private val RESET_TOKEN_TTL: Duration = Duration.ofMinutes(30)
private const val JWT_ISSUER = "evola"

/**
 * Full auth rebuild per 01_PRODUCT_SPEC.md §1.1-1.2 and 03_API_CONTRACT.md: register, login with
 * account lockout, password reset (request/confirm), refresh/logout. Replaces the old signup-only
 * endpoint. `refresh_tokens` isn't in the kit's own DB schema but its API contract requires
 * revocable refresh tokens, which need persistence - added pragmatically (see plan notes).
 */
class AuthService(
    private val database: Database,
    private val jwtSecret: String,
) {

    private fun isValidPassword(password: String): Boolean =
        password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }

    suspend fun register(request: RegisterRequest): RegisterOutcome {
        val email = request.email.trim().lowercase()
        val fullName = request.fullName.trim()

        if (!FULL_NAME_PATTERN.matches(fullName)) {
            return RegisterOutcome.Invalid("Full name must be 2-60 characters (letters, spaces, hyphens, apostrophes).")
        }
        if (!EMAIL_PATTERN.matches(email)) return RegisterOutcome.Invalid("Not a valid email address.")
        if (!isValidPassword(request.password)) {
            return RegisterOutcome.Invalid("Password must be at least 8 characters with at least one letter and one digit.")
        }

        return newSuspendedTransaction(Dispatchers.IO, database) {
            val userId = UUID.randomUUID()
            val now = Instant.now()
            try {
                UsersTable.insert {
                    it[id] = userId
                    it[this.email] = email
                    it[passwordHash] = BCrypt.hashpw(request.password, BCrypt.gensalt())
                    it[this.fullName] = fullName
                    it[emailVerified] = false
                    it[onboardingCompleted] = false
                    it[failedLoginCount] = 0
                    it[lockedUntil] = null
                    it[lastFailedLoginAt] = null
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } catch (e: ExposedSQLException) {
                if (isUniqueViolation(e)) return@newSuspendedTransaction RegisterOutcome.EmailTaken
                throw e
            }
            RegisterOutcome.Created(issueTokens(userId, fullName, email, onboardingCompleted = false))
        }
    }

    suspend fun login(request: LoginRequest): LoginOutcome {
        val email = request.email.trim().lowercase()
        return newSuspendedTransaction(Dispatchers.IO, database) {
            val row = UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
                ?: return@newSuspendedTransaction LoginOutcome.InvalidCredentials

            val now = Instant.now()
            val lockedUntil = row[UsersTable.lockedUntil]
            if (lockedUntil != null && lockedUntil.isAfter(now)) {
                return@newSuspendedTransaction LoginOutcome.Locked(minutesRemaining(now, lockedUntil))
            }

            val userId = row[UsersTable.id]
            val passwordMatches = BCrypt.checkpw(request.password, row[UsersTable.passwordHash])

            if (!passwordMatches) {
                val lastFailed = row[UsersTable.lastFailedLoginAt]
                val withinWindow = lastFailed != null && Duration.between(lastFailed, now) <= LOCKOUT_WINDOW
                val newCount = if (withinWindow) row[UsersTable.failedLoginCount] + 1 else 1
                val newLockedUntil = if (newCount >= LOCKOUT_THRESHOLD) now.plus(LOCKOUT_DURATION) else null

                UsersTable.update({ UsersTable.id eq userId }) {
                    it[failedLoginCount] = newCount
                    it[lastFailedLoginAt] = now
                    it[this.lockedUntil] = newLockedUntil
                    it[updatedAt] = now
                }

                return@newSuspendedTransaction if (newLockedUntil != null) {
                    LoginOutcome.Locked(minutesRemaining(now, newLockedUntil))
                } else {
                    LoginOutcome.InvalidCredentials
                }
            }

            UsersTable.update({ UsersTable.id eq userId }) {
                it[failedLoginCount] = 0
                it[lastFailedLoginAt] = null
                it[this.lockedUntil] = null
                it[updatedAt] = now
            }

            LoginOutcome.Success(
                issueTokens(userId, row[UsersTable.fullName], email, row[UsersTable.onboardingCompleted]),
            )
        }
    }

    suspend fun requestPasswordReset(email: String) {
        val normalized = email.trim().lowercase()
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = UsersTable.selectAll().where { UsersTable.email eq normalized }.singleOrNull()
                ?: return@newSuspendedTransaction

            val userId = row[UsersTable.id]
            val rawToken = randomToken()
            val now = Instant.now()
            PasswordResetTokensTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = userId
                it[tokenHash] = sha256(rawToken)
                it[expiresAt] = now.plus(RESET_TOKEN_TTL)
                it[usedAt] = null
                it[createdAt] = now
            }
            // No email provider configured yet - logging the raw token as the dev stand-in for
            // real delivery (flagged as a follow-up in the plan).
            println("[DEV] password reset token for $normalized: $rawToken")
        }
    }

    suspend fun confirmPasswordReset(request: PasswordResetConfirmPayload): PasswordResetConfirmOutcome {
        if (!isValidPassword(request.newPassword)) {
            return PasswordResetConfirmOutcome.Invalid(
                "Password must be at least 8 characters with at least one letter and one digit.",
            )
        }
        val tokenHash = sha256(request.token)
        return newSuspendedTransaction(Dispatchers.IO, database) {
            val now = Instant.now()
            val row = PasswordResetTokensTable
                .selectAll().where {
                    (PasswordResetTokensTable.tokenHash eq tokenHash) and (PasswordResetTokensTable.usedAt.isNull())
                }
                .singleOrNull() ?: return@newSuspendedTransaction PasswordResetConfirmOutcome.TokenInvalidOrExpired

            if (row[PasswordResetTokensTable.expiresAt].isBefore(now)) {
                return@newSuspendedTransaction PasswordResetConfirmOutcome.TokenInvalidOrExpired
            }

            val userId = row[PasswordResetTokensTable.userId]
            UsersTable.update({ UsersTable.id eq userId }) {
                it[passwordHash] = BCrypt.hashpw(request.newPassword, BCrypt.gensalt())
                // Proving ownership via the emailed reset token should also clear any lockout -
                // otherwise a locked-out user could reset their password and still be unable to log in.
                it[failedLoginCount] = 0
                it[lockedUntil] = null
                it[lastFailedLoginAt] = null
                it[updatedAt] = now
            }
            PasswordResetTokensTable.update({ PasswordResetTokensTable.id eq row[PasswordResetTokensTable.id] }) {
                it[usedAt] = now
            }
            RefreshTokensTable.update({ (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull() }) {
                it[revokedAt] = now
            }
            PasswordResetConfirmOutcome.Success
        }
    }

    suspend fun refresh(rawRefreshToken: String): String? {
        val tokenHash = sha256(rawRefreshToken)
        return newSuspendedTransaction(Dispatchers.IO, database) {
            val now = Instant.now()
            val row = RefreshTokensTable
                .selectAll().where { (RefreshTokensTable.tokenHash eq tokenHash) and RefreshTokensTable.revokedAt.isNull() }
                .singleOrNull() ?: return@newSuspendedTransaction null

            if (row[RefreshTokensTable.expiresAt].isBefore(now)) return@newSuspendedTransaction null

            createAccessToken(row[RefreshTokensTable.userId])
        }
    }

    suspend fun logout(rawRefreshToken: String) {
        val tokenHash = sha256(rawRefreshToken)
        newSuspendedTransaction(Dispatchers.IO, database) {
            RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq tokenHash }) {
                it[revokedAt] = Instant.now()
            }
        }
    }

    suspend fun getUser(userId: String): UserResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            UsersTable.selectAll().where { UsersTable.id eq UUID.fromString(userId) }
                .singleOrNull()
                ?.let {
                    UserResponse(
                        id = it[UsersTable.id].toString(),
                        fullName = it[UsersTable.fullName],
                        email = it[UsersTable.email],
                        onboardingCompleted = it[UsersTable.onboardingCompleted],
                    )
                }
        }

    private fun issueTokens(userId: UUID, fullName: String, email: String, onboardingCompleted: Boolean): AuthTokensResponse =
        AuthTokensResponse(
            accessToken = createAccessToken(userId),
            refreshToken = createRefreshToken(userId),
            user = UserResponse(userId.toString(), fullName, email, onboardingCompleted),
        )

    private fun createAccessToken(userId: UUID): String =
        JWT.create()
            .withIssuer(JWT_ISSUER)
            .withSubject(userId.toString())
            .withExpiresAt(Instant.now().plus(ACCESS_TOKEN_TTL))
            .sign(Algorithm.HMAC256(jwtSecret))

    private fun createRefreshToken(userId: UUID): String {
        val raw = randomToken()
        val now = Instant.now()
        RefreshTokensTable.insert {
            it[id] = UUID.randomUUID()
            it[this.userId] = userId
            it[tokenHash] = sha256(raw)
            it[expiresAt] = now.plus(REFRESH_TOKEN_TTL)
            it[revokedAt] = null
            it[createdAt] = now
        }
        return raw
    }

    private fun minutesRemaining(now: Instant, until: Instant): Long {
        val secondsRemaining = Duration.between(now, until).seconds.coerceAtLeast(0)
        return (secondsRemaining + 59) / 60
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun isUniqueViolation(e: ExposedSQLException): Boolean =
        (e.cause as? SQLException)?.sqlState == "23505"
}
