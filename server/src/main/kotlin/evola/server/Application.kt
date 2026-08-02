package evola.server

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import evola.integrations.persistence.DatabaseFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

private const val JWT_ISSUER = "evola"

private fun requiredEnv(name: String): String =
    System.getenv(name) ?: error("Missing required environment variable: $name")

fun main() {
    val databaseUrl = requiredEnv("DATABASE_URL")
    val databaseUser = System.getenv("DATABASE_USER") ?: "evola"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "evola"
    val port = (System.getenv("SERVER_PORT") ?: "8081").toInt()
    val anthropicApiKey = requiredEnv("ANTHROPIC_API_KEY")
    val jwtSecret = requiredEnv("JWT_SECRET")

    val uploadsDir = System.getenv("UPLOADS_DIR") ?: "uploads"

    val database = DatabaseFactory.connect(databaseUrl, databaseUser, databasePassword)
    val anthropicClient = AnthropicOkHttpClient.builder().apiKey(anthropicApiKey).build()
    val extractionWorker = ExtractionWorker(database, anthropicClient)
    val materialService = MaterialService(database, uploadsDir, onJobQueued = { extractionWorker.wake.trySend(Unit) })
    val authService = AuthService(database, jwtSecret)
    val goalService = GoalService(database)

    println("Evola :server starting on 127.0.0.1:$port ...")
    embeddedServer(CIO, port = port, host = "127.0.0.1") {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(ErrorBody("INTERNAL_ERROR", cause.message ?: "Internal error")),
                )
            }
        }
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).withIssuer(JWT_ISSUER).build())
                validate { credential -> if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null }
            }
        }
        extractionWorker.start(this)
        routing {
            healthRoutes()
            materialRoutes(materialService)
            authRoutes(authService)
            goalRoutes(goalService)
        }
    }.start(wait = true)
}
