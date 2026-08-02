package evola.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RegisterWireRequest(@SerialName("full_name") val fullName: String, val email: String, val password: String)

@Serializable
private data class LoginWireRequest(val email: String, val password: String)

@Serializable
private data class PasswordResetRequestWirePayload(val email: String)

@Serializable
private data class PasswordResetConfirmWirePayload(val token: String, @SerialName("new_password") val newPassword: String)

@Serializable
private data class RefreshWireRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
private data class UserWireResponse(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean,
)

@Serializable
private data class AuthTokensWireResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val user: UserWireResponse,
)

@Serializable
private data class WireErrorBody(
    val code: String,
    val message: String,
    @SerialName("minutes_remaining") val minutesRemaining: Long? = null,
)

@Serializable
private data class WireErrorResponse(val error: WireErrorBody)

@Serializable
private data class AccessTokenWireResponse(@SerialName("access_token") val accessToken: String)

class HttpAuthRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : AuthRepository {

    override suspend fun register(fullName: String, email: String, password: String): AuthResult {
        val response = httpClient.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterWireRequest(fullName, email, password))
        }
        return when (response.status) {
            HttpStatusCode.Created -> AuthResult.Success(response.body<AuthTokensWireResponse>().toDomain())
            HttpStatusCode.Conflict -> AuthResult.EmailTaken
            HttpStatusCode.BadRequest -> AuthResult.ValidationError(response.errorBody().message)
            else -> error("Register failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun login(email: String, password: String): AuthResult {
        val response = httpClient.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginWireRequest(email, password))
        }
        return when (response.status) {
            HttpStatusCode.OK -> AuthResult.Success(response.body<AuthTokensWireResponse>().toDomain())
            HttpStatusCode.Unauthorized -> AuthResult.InvalidCredentials
            HttpStatusCode.Locked -> AuthResult.AccountLocked(response.errorBody().minutesRemaining ?: 15L)
            else -> error("Login failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun requestPasswordReset(email: String) {
        httpClient.post("$baseUrl/auth/password-reset/request") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetRequestWirePayload(email))
        }
    }

    override suspend fun logout(refreshToken: String) {
        httpClient.post("$baseUrl/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(RefreshWireRequest(refreshToken))
        }
    }

    override suspend fun confirmPasswordReset(token: String, newPassword: String): PasswordResetConfirmResult {
        val response = httpClient.post("$baseUrl/auth/password-reset/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetConfirmWirePayload(token, newPassword))
        }
        return when (response.status) {
            HttpStatusCode.OK -> PasswordResetConfirmResult.Success
            HttpStatusCode.BadRequest -> {
                val error = response.errorBody()
                if (error.code == "TOKEN_INVALID_OR_EXPIRED") {
                    PasswordResetConfirmResult.TokenInvalidOrExpired
                } else {
                    PasswordResetConfirmResult.ValidationError(error.message)
                }
            }
            else -> error("Confirm password reset failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun refresh(refreshToken: String): String? {
        val response = httpClient.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshWireRequest(refreshToken))
        }
        return if (response.status == HttpStatusCode.OK) response.body<AccessTokenWireResponse>().accessToken else null
    }

    override suspend fun getCurrentUser(accessToken: String): AuthUser? {
        val response = httpClient.get("$baseUrl/users/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        val user = response.body<UserWireResponse>()
        return AuthUser(user.id, user.fullName, user.email, user.onboardingCompleted)
    }

    private suspend fun HttpResponse.errorBody(): WireErrorBody = body<WireErrorResponse>().error

    private fun AuthTokensWireResponse.toDomain() = AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = AuthUser(user.id, user.fullName, user.email, user.onboardingCompleted),
    )
}
