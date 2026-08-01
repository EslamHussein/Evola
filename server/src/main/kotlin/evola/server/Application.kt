package evola.server

import evola.integrations.persistence.DatabaseFactory
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

private fun requiredEnv(name: String): String =
    System.getenv(name) ?: error("Missing required environment variable: $name")

fun main() {
    val databaseUrl = requiredEnv("DATABASE_URL")
    val databaseUser = System.getenv("DATABASE_USER") ?: "evola"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "evola"
    val port = (System.getenv("SERVER_PORT") ?: "8081").toInt()

    val database = DatabaseFactory.connect(databaseUrl, databaseUser, databasePassword)
    val materialService = MaterialService(database)
    val authService = AuthService(database)

    println("Evola :server starting on 127.0.0.1:$port ...")
    embeddedServer(CIO, port = port, host = "127.0.0.1") {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Internal error")))
            }
        }
        routing {
            healthRoutes()
            materialRoutes(materialService)
            authRoutes(authService)
        }
    }.start(wait = true)
}
