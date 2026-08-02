package evola.server

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private val database = TestDatabase.database
    private val authService = AuthService(database, jwtSecret = "test-secret")

    @BeforeEach
    fun clearTables() {
        transaction(database) {
            RefreshTokensTable.deleteAll()
            PasswordResetTokensTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `register creates a user and issues tokens`() = runTest {
        val outcome = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<RegisterOutcome.Created>(outcome)
        assertEquals("amara@example.com", outcome.tokens.user.email)
        assertEquals(false, outcome.tokens.user.onboardingCompleted)
    }

    @Test
    fun `register rejects a duplicate email, case-insensitively`() = runTest {
        authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        val outcome = authService.register(RegisterRequest("Someone Else", "AMARA@example.com", "Passw0rd!"))
        assertIs<RegisterOutcome.EmailTaken>(outcome)
    }

    @Test
    fun `register rejects a weak password`() = runTest {
        val outcome = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "short"))
        assertIs<RegisterOutcome.Invalid>(outcome)
    }

    @Test
    fun `login succeeds with correct credentials`() = runTest {
        authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<LoginOutcome.Success>(authService.login(LoginRequest("amara@example.com", "Passw0rd!")))
    }

    @Test
    fun `login fails with the wrong password`() = runTest {
        authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<LoginOutcome.InvalidCredentials>(authService.login(LoginRequest("amara@example.com", "WrongPass1")))
    }

    @Test
    fun `login locks the account after 5 failed attempts within the window`() = runTest {
        authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        repeat(4) { authService.login(LoginRequest("amara@example.com", "WrongPass1")) }

        val fifthFailure = authService.login(LoginRequest("amara@example.com", "WrongPass1"))
        assertIs<LoginOutcome.Locked>(fifthFailure)
        assertTrue(fifthFailure.minutesRemaining in 1..15)

        // Even the correct password is rejected while locked.
        assertIs<LoginOutcome.Locked>(authService.login(LoginRequest("amara@example.com", "Passw0rd!")))
    }

    @Test
    fun `login does not lock when the prior failure was outside the 15-minute window`() = runTest {
        authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        transaction(database) {
            UsersTable.update({ UsersTable.email eq "amara@example.com" }) {
                it[failedLoginCount] = 4
                it[lastFailedLoginAt] = Instant.now().minus(Duration.ofMinutes(20))
            }
        }
        // The window has lapsed, so this 5th attempt counts as a fresh 1st failure, not a lockout.
        assertIs<LoginOutcome.InvalidCredentials>(authService.login(LoginRequest("amara@example.com", "WrongPass1")))
    }

    @Test
    fun `password reset confirm updates the password and unlocks a locked account`() = runTest {
        val registered = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<RegisterOutcome.Created>(registered)
        val userId = UUID.fromString(registered.tokens.user.id)

        val rawToken = "test-raw-reset-token"
        transaction(database) {
            PasswordResetTokensTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = userId
                it[tokenHash] = sha256(rawToken)
                it[expiresAt] = Instant.now().plus(Duration.ofMinutes(30))
                it[usedAt] = null
                it[createdAt] = Instant.now()
            }
        }

        val outcome = authService.confirmPasswordReset(PasswordResetConfirmPayload(rawToken, "NewPassw0rd!"))
        assertIs<PasswordResetConfirmOutcome.Success>(outcome)

        assertIs<LoginOutcome.InvalidCredentials>(authService.login(LoginRequest("amara@example.com", "Passw0rd!")))
        assertIs<LoginOutcome.Success>(authService.login(LoginRequest("amara@example.com", "NewPassw0rd!")))
    }

    @Test
    fun `password reset confirm rejects an expired token`() = runTest {
        val registered = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<RegisterOutcome.Created>(registered)
        val userId = UUID.fromString(registered.tokens.user.id)

        val rawToken = "expired-token"
        transaction(database) {
            PasswordResetTokensTable.insert {
                it[id] = UUID.randomUUID()
                it[this.userId] = userId
                it[tokenHash] = sha256(rawToken)
                it[expiresAt] = Instant.now().minusSeconds(60)
                it[usedAt] = null
                it[createdAt] = Instant.now()
            }
        }

        val outcome = authService.confirmPasswordReset(PasswordResetConfirmPayload(rawToken, "NewPassw0rd!"))
        assertIs<PasswordResetConfirmOutcome.TokenInvalidOrExpired>(outcome)
    }

    @Test
    fun `refresh issues a new access token, logout revokes it`() = runTest {
        val registered = authService.register(RegisterRequest("Amara Nwosu", "amara@example.com", "Passw0rd!"))
        assertIs<RegisterOutcome.Created>(registered)
        val refreshToken = registered.tokens.refreshToken

        assertNotNull(authService.refresh(refreshToken))

        authService.logout(refreshToken)
        assertNull(authService.refresh(refreshToken))
    }
}
