package evola.presentation.miniapp

import evola.learneridentity.application.RegisterLearnerHandler
import evola.tutoring.application.SetLearningModeHandler
import evola.tutoring.application.StartLearningSessionHandler
import evola.tutoring.application.SubmitSessionAnswerHandler
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

/**
 * Localhost-only REST API for the Telegram Mini App (Learn tab, Milestone 5a). Caddy is the sole
 * public HTTPS listener and reverse-proxies every `/api` route here — nothing in this module ever
 * binds a public port. Same layering rule as the Telegram bot adapter: routes only dispatch to
 * existing application-layer UseCase handlers, no business logic lives here.
 */
fun buildMiniAppServer(
    port: Int,
    botToken: String,
    allowedOrigins: List<String>,
    registerLearnerHandler: RegisterLearnerHandler,
    startLearningSessionHandler: StartLearningSessionHandler,
    submitSessionAnswerHandler: SubmitSessionAnswerHandler,
    setLearningModeHandler: SetLearningModeHandler,
): EmbeddedServer<*, *> {
    val auth = MiniAppAuth(botToken, registerLearnerHandler)

    return embeddedServer(CIO, port = port, host = "127.0.0.1") {
        configureMiniAppServer(auth, allowedOrigins, startLearningSessionHandler, submitSessionAnswerHandler, setLearningModeHandler)
    }
}

fun Application.configureMiniAppServer(
    auth: MiniAppAuth,
    allowedOrigins: List<String>,
    startLearningSessionHandler: StartLearningSessionHandler,
    submitSessionAnswerHandler: SubmitSessionAnswerHandler,
    setLearningModeHandler: SetLearningModeHandler,
) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

    install(CORS) {
        // allowHost expects a bare host[:port], not a full URL — strip any scheme prefix callers pass.
        allowedOrigins.forEach { origin -> allowHost(origin.substringAfter("://"), schemes = listOf("http", "https")) }
        allowHeader("Authorization")
        allowHeader("Content-Type")
        allowMethod(io.ktor.http.HttpMethod.Post)
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal error"))
        }
    }

    routing {
        sessionRoutes(auth, startLearningSessionHandler, submitSessionAnswerHandler, setLearningModeHandler)
    }
}
