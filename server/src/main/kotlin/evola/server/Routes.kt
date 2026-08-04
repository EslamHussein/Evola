package evola.server

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.toByteArray

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
    }
}

fun Route.materialRoutes(materialService: MaterialService) {
    authenticate("auth-jwt") {
        post("/materials/upload") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            var goalId: String? = null
            var fileName: String? = null
            var fileBytes: ByteArray? = null
            var organizationMode: String? = null
            var aiInstructions: String? = null
            var resourceType: String? = null

            call.receiveMultipart().forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> when (part.name) {
                        "goal_id" -> goalId = part.value
                        "organization_mode" -> organizationMode = part.value
                        "ai_instructions" -> aiInstructions = part.value
                        "resource_type" -> resourceType = part.value
                    }
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: "upload"
                        fileBytes = part.provider().toByteArray()
                    }
                    else -> {}
                }
                part.dispose()
            }

            val goal = goalId
            val bytes = fileBytes
            if (goal == null || bytes == null) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing goal_id or file"))
            }

            when (
                val outcome = materialService.uploadMaterial(
                    userId, goal, fileName ?: "upload", bytes,
                    organizationMode = organizationMode ?: "auto",
                    aiInstructions = aiInstructions,
                    resourceType = resourceType,
                )
            ) {
                is UploadOutcome.Created -> call.respond(
                    HttpStatusCode.Accepted,
                    MaterialUploadResponse(outcome.materialId, outcome.status),
                )
                UploadOutcome.GoalNotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Goal not found"))
                UploadOutcome.UnsupportedFileType -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("UNSUPPORTED_FILE_TYPE", "Only PDF and DOCX are supported.")),
                )
                UploadOutcome.FileTooLarge -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("FILE_TOO_LARGE", "Files must be under 25MB.")),
                )
                UploadOutcome.PasswordProtected -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("PASSWORD_PROTECTED", "This PDF is password-protected. Remove the password and try again.")),
                )
                UploadOutcome.CorruptedFile -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("CORRUPTED_FILE", "This file couldn't be read. It may be corrupted.")),
                )
                UploadOutcome.NoExtractableText -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("NO_EXTRACTABLE_TEXT", "This looks like a scanned file. Upload a text-based PDF or DOCX.")),
                )
                is UploadOutcome.DuplicateFile -> call.respond(
                    HttpStatusCode.Conflict,
                    DuplicateFileResponse(existingMaterialId = outcome.existingMaterialId),
                )
            }
        }

        post("/materials/upload-text") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<MaterialTextUploadRequest>()

            when (
                val outcome = materialService.uploadTextMaterial(
                    userId, request.goalId, request.fileName, request.text,
                    organizationMode = request.organizationMode ?: "auto",
                    aiInstructions = request.aiInstructions,
                    resourceType = request.resourceType,
                )
            ) {
                is UploadOutcome.Created -> call.respond(
                    HttpStatusCode.Accepted,
                    MaterialUploadResponse(outcome.materialId, outcome.status),
                )
                UploadOutcome.GoalNotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Goal not found"))
                UploadOutcome.UnsupportedFileType -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("UNSUPPORTED_FILE_TYPE", "Only PDF and DOCX are supported.")),
                )
                UploadOutcome.FileTooLarge -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("FILE_TOO_LARGE", "Pasted text must be under 200,000 characters.")),
                )
                UploadOutcome.PasswordProtected -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("PASSWORD_PROTECTED", "This PDF is password-protected. Remove the password and try again.")),
                )
                UploadOutcome.CorruptedFile -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("CORRUPTED_FILE", "This file couldn't be read. It may be corrupted.")),
                )
                UploadOutcome.NoExtractableText -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("NO_EXTRACTABLE_TEXT", "Paste at least a few words of text.")),
                )
                is UploadOutcome.DuplicateFile -> call.respond(
                    HttpStatusCode.Conflict,
                    DuplicateFileResponse(existingMaterialId = outcome.existingMaterialId),
                )
            }
        }

        get("/materials") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.OK, materialService.listMaterials(userId))
        }

        get("/materials/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val materialId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing material id"))
            val detail = materialService.getMaterial(materialId)
            if (detail == null || detail.material.userId != userId) {
                return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
            }
            call.respond(HttpStatusCode.OK, detail)
        }

        get("/materials/{id}/status") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val materialId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing material id"))
            val detail = materialService.getMaterial(materialId)
            if (detail == null || detail.material.userId != userId) {
                return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found"))
            }
            call.respond(HttpStatusCode.OK, MaterialStatusResponse(detail.material.status.name, detail.material.pageCount))
        }

        post("/materials/{id}/reprocess") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val materialId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing material id"))
            val requeued = materialService.reprocess(userId, materialId)
            if (requeued) {
                call.respond(HttpStatusCode.Accepted, mapOf("status" to "queued"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Material not found or not currently failed"))
            }
        }

        // Unified lesson-details destination (Phase 6): not nested under /materials since a
        // lesson is reached both from the Materials tab and the Study tab, but lives here since
        // MaterialService already owns the lesson/vocab-progress join it needs.
        get("/lessons/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val lessonId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing lesson id"))
            val detail = materialService.getLessonDetail(userId, lessonId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Lesson not found"))
            call.respond(HttpStatusCode.OK, detail)
        }
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

fun Route.goalRoutes(goalService: GoalService) {
    authenticate("auth-jwt") {
        post("/goals") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<CreateGoalRequest>()
            when (val outcome = goalService.createGoal(userId, request)) {
                is CreateGoalOutcome.Created -> call.respond(HttpStatusCode.Created, outcome.goal)
                CreateGoalOutcome.ActiveGoalExists -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(ErrorBody("ACTIVE_GOAL_EXISTS", "You already have an active goal.")),
                )
                is CreateGoalOutcome.Invalid -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("VALIDATION_ERROR", outcome.message)),
                )
            }
        }

        patch("/goals/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val goalId = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing goal id"))
            val request = call.receive<UpdateGoalRequest>()
            when (val outcome = goalService.updateGoal(userId, goalId, request)) {
                is UpdateGoalOutcome.Updated -> call.respond(HttpStatusCode.OK, outcome.goal)
                UpdateGoalOutcome.NotFound -> call.respond(HttpStatusCode.NotFound)
                is UpdateGoalOutcome.Invalid -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(ErrorBody("VALIDATION_ERROR", outcome.message)),
                )
            }
        }

        get("/goals/{id}") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val goalId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing goal id"))
            val goal = goalService.getGoal(userId, goalId) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, goal)
        }

        // Convenience lookup so the client can discover its active goal (and id) after login
        // without already knowing the goal's id - not in 03_API_CONTRACT.md verbatim, but
        // required for session-restore to know whether onboarding is truly complete.
        get("/goals/active") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val goal = goalService.getActiveGoal(userId) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, goal)
        }

        get("/goals/{id}/lessons") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val goalId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing goal id"))
            val lessons = goalService.listLessonsForGoal(userId, goalId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Goal not found"))
            call.respond(HttpStatusCode.OK, lessons)
        }
    }
}

fun Route.vocabularyRoutes(vocabularyService: VocabularyService) {
    authenticate("auth-jwt") {
        post("/lessons/{id}/vocabulary/session") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val lessonId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing lesson id"))
            val session = vocabularyService.startOrResumeSession(userId, lessonId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Lesson not found"))
            call.respond(HttpStatusCode.Created, session)
        }

        get("/lessons/{id}/vocabulary") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val lessonId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing lesson id"))
            val items = vocabularyService.listVocabulary(userId, lessonId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Lesson not found"))
            call.respond(HttpStatusCode.OK, items)
        }

        post("/vocabulary-sessions/{id}/answer") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val sessionId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session id"))
            val request = call.receive<VocabularyAnswerRequest>()
            val result = vocabularyService.answer(userId, sessionId, request)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session or item not found"))
            call.respond(HttpStatusCode.OK, result)
        }

        post("/vocabulary-sessions/{id}/complete") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val sessionId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session id"))
            val result = vocabularyService.complete(userId, sessionId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            call.respond(HttpStatusCode.OK, result)
        }

        patch("/vocabulary-items/{id}/flags") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)
            val itemId = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing item id"))
            val request = call.receive<VocabularyFlagsRequest>()
            val result = vocabularyService.updateFlags(userId, itemId, request)
                ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "Vocabulary item not found"))
            call.respond(HttpStatusCode.OK, result)
        }
    }
}

fun Route.grammarRoutes(grammarService: GrammarService) {
    authenticate("auth-jwt") {
        get("/lessons/{id}/grammar") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val lessonId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing lesson id"))
            val topics = grammarService.listTopics(userId, lessonId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Lesson not found"))
            call.respond(HttpStatusCode.OK, topics)
        }

        post("/grammar-topics/{id}/exercise-session") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val topicId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing topic id"))
            val session = grammarService.startOrResumeSession(userId, topicId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Topic not found"))
            call.respond(HttpStatusCode.Created, session)
        }

        post("/grammar-sessions/{id}/answer") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val sessionId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session id"))
            val request = call.receive<GrammarAnswerRequest>()
            val result = grammarService.answer(userId, sessionId, request)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session or exercise not found"))
            call.respond(HttpStatusCode.OK, result)
        }

        post("/grammar-sessions/{id}/complete") {
            val userId = call.principal<JWTPrincipal>()?.payload?.subject
                ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val sessionId = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing session id"))
            val result = grammarService.complete(userId, sessionId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
