package evola.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.UUID

@Serializable
data class SignUpRequest(val email: String, val password: String)

@Serializable
data class SignUpResponse(val userId: String)

sealed interface SignUpOutcome {
    data class Created(val userId: String) : SignUpOutcome
    data object EmailAlreadyTaken : SignUpOutcome
    data class Invalid(val reason: String) : SignUpOutcome
}

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class AuthService(private val database: Database) {

    suspend fun signUp(request: SignUpRequest): SignUpOutcome {
        val email = request.email.trim().lowercase()

        if (!EMAIL_PATTERN.matches(email)) {
            return SignUpOutcome.Invalid("Not a valid email address.")
        }
        if (request.password.length < 8) {
            return SignUpOutcome.Invalid("Password must be at least 8 characters.")
        }

        return newSuspendedTransaction(Dispatchers.IO, database) {
            val alreadyExists = UsersTable
                .selectAll().where { UsersTable.email eq email }
                .any()
            if (alreadyExists) {
                return@newSuspendedTransaction SignUpOutcome.EmailAlreadyTaken
            }

            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[this.email] = email
                it[passwordHash] = BCrypt.hashpw(request.password, BCrypt.gensalt())
                it[createdAt] = Instant.now()
            }
            SignUpOutcome.Created(userId.toString())
        }
    }
}
