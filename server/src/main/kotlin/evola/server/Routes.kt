package evola.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}

fun Route.materialRoutes(materialService: MaterialService) {
    post("/api/materials") {
        val request = call.receive<UploadMaterialRequest>()
        val response = materialService.uploadMaterial(request)
        call.respond(HttpStatusCode.OK, response)
    }

    get("/api/materials") {
        val userId = call.request.queryParameters["userId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId query parameter"))
        call.respond(HttpStatusCode.OK, materialService.listMaterials(userId))
    }

    get("/api/materials/{id}") {
        val materialId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing material id"))
        val detail = materialService.getMaterial(materialId)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
        call.respond(HttpStatusCode.OK, detail)
    }
}

fun Route.authRoutes(authService: AuthService) {
    post("/auth/register") {
        val request = call.receive<RegisterRequest>()
        when (val outcome = authService.register(request)) {
            is RegisterOutcome.Created -> call.respond(HttpStatusCode.Created, outcome.tokens)
            RegisterOutcome.EmailTaken -> call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(ErrorBody("EMAIL_TAKEN", "An account with this email already exists.")),
            )
            is RegisterOutcome.Invalid -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("VALIDATION_ERROR", outcome.message)),
            )
        }
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        when (val outcome = authService.login(request)) {
            is LoginOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.tokens)
            LoginOutcome.InvalidCredentials -> call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorBody("INVALID_CREDENTIALS", "Email or password is incorrect.")),
            )
            is LoginOutcome.Locked -> call.respond(
                HttpStatusCode.Locked,
                ErrorResponse(
                    ErrorBody(
                        "ACCOUNT_LOCKED",
                        "Too many attempts. Try again in ${outcome.minutesRemaining} minutes.",
                        minutesRemaining = outcome.minutesRemaining,
                    ),
                ),
            )
        }
    }

    post("/auth/password-reset/request") {
        val request = call.receive<PasswordResetRequestPayload>()
        authService.requestPasswordReset(request.email)
        call.respond(HttpStatusCode.OK, mapOf("message" to "If that email exists, a reset link has been sent."))
    }

    post("/auth/password-reset/confirm") {
        val request = call.receive<PasswordResetConfirmPayload>()
        when (val outcome = authService.confirmPasswordReset(request)) {
            PasswordResetConfirmOutcome.Success -> call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated."))
            PasswordResetConfirmOutcome.TokenInvalidOrExpired -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("TOKEN_INVALID_OR_EXPIRED", "This reset link is invalid or has expired.")),
            )
            is PasswordResetConfirmOutcome.Invalid -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorBody("VALIDATION_ERROR", outcome.message)),
            )
        }
    }

    post("/auth/refresh") {
        val request = call.receive<RefreshRequest>()
        val accessToken = authService.refresh(request.refreshToken)
        if (accessToken != null) {
            call.respond(HttpStatusCode.OK, mapOf("access_token" to accessToken))
        } else {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorBody("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired.")),
            )
        }
    }

    post("/auth/logout") {
        val request = call.receive<RefreshRequest>()
        authService.logout(request.refreshToken)
        call.respond(HttpStatusCode.NoContent)
    }

    authenticate("auth-jwt") {
        get("/users/me") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val user = authService.getUser(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, user)
        }
    }
}
