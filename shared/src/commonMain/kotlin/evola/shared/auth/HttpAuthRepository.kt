package evola.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SignUpWireRequest(val email: String, val password: String)

@Serializable
private data class SignUpWireResponse(val userId: String)

class HttpAuthRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : AuthRepository {

    override suspend fun signUp(request: SignUpRequest): SignUpResult {
        val response: HttpResponse = httpClient.post("$baseUrl/api/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignUpWireRequest(request.email, request.password))
        }
        return when (response.status) {
            HttpStatusCode.Created -> SignUpResult.Success(response.body<SignUpWireResponse>().userId)
            HttpStatusCode.Conflict -> SignUpResult.EmailAlreadyTaken
            else -> error("Sign up failed: HTTP ${response.status.value}")
        }
    }
}
