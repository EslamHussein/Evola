package evola.presentation.miniapp

import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.MatchPair
import evola.tutoring.application.SetLearningModeHandler
import evola.tutoring.application.SetLearningModeCommand
import evola.tutoring.application.StartLearningSessionCommand
import evola.tutoring.application.StartLearningSessionHandler
import evola.tutoring.application.SubmitSessionAnswerCommand
import evola.tutoring.application.SubmitSessionAnswerHandler
import evola.tutoring.application.SubmitSessionAnswerResult
import evola.tutoring.application.StartSessionResult
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.LearningMode
import evola.tutoring.domain.SessionBudgetType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

/** Every route: validate initData -> resolve learnerId -> parse body -> call the existing UseCase -> map DomainResult to JSON. */
fun Route.sessionRoutes(
    auth: MiniAppAuth,
    startLearningSessionHandler: StartLearningSessionHandler,
    submitSessionAnswerHandler: SubmitSessionAnswerHandler,
    setLearningModeHandler: SetLearningModeHandler,
) {
    route("/api") {
        post("/session/learn/start") {
            val learnerId = auth.resolveLearnerId(call) ?: return@post
            val request = call.receive<StartSessionRequest>()

            val budgetType = runCatching { SessionBudgetType.valueOf(request.budgetType) }.getOrNull()
            if (budgetType == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid budgetType: ${request.budgetType}"))
                return@post
            }

            val result = startLearningSessionHandler.handle(
                StartLearningSessionCommand(
                    learnerId = learnerId,
                    budgetType = budgetType,
                    budgetValue = request.budgetValue,
                    allowedKinds = parseAllowedKinds(request.allowedKinds),
                    difficultyOverride = parseDifficulty(request.difficulty),
                ),
            )
            respondResult(call, result) { it.toResponse() }
        }

        post("/session/learn/answer") {
            val learnerId = auth.resolveLearnerId(call) ?: return@post
            val request = call.receive<SubmitSessionAnswerRequest>()

            val sessionRunId = runCatching { LearningSessionRunId(UUID.fromString(request.sessionRunId)) }.getOrNull()
            val tutoringSessionId = runCatching { TutoringSessionId(UUID.fromString(request.tutoringSessionId)) }.getOrNull()
            if (sessionRunId == null || tutoringSessionId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid sessionRunId/tutoringSessionId"))
                return@post
            }

            val result = submitSessionAnswerHandler.handle(
                SubmitSessionAnswerCommand(learnerId, sessionRunId, tutoringSessionId, request.rawAnswer),
            )
            respondResult(call, result) { it.toResponse() }
        }

        post("/mode") {
            val learnerId = auth.resolveLearnerId(call) ?: return@post
            val request = call.receive<SetModeRequest>()
            val mode = runCatching { LearningMode.valueOf(request.mode) }.getOrNull()
            if (mode == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid mode: ${request.mode}"))
                return@post
            }
            setLearningModeHandler.handle(SetLearningModeCommand(learnerId, mode))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun <T> respondResult(call: ApplicationCall, result: DomainResult<T>, toResponse: (T) -> Any) {
    when (result) {
        is DomainResult.Ok -> call.respond(toResponse(result.value))
        is DomainResult.Err -> {
            val status = when (result.error) {
                is DomainError.NotFound -> HttpStatusCode.NotFound
                is DomainError.ValidationFailed -> HttpStatusCode.BadRequest
                is DomainError.Conflict -> HttpStatusCode.Conflict
            }
            call.respond(status, ErrorResponse(result.error.message))
        }
    }
}

private fun parseAllowedKinds(raw: List<String>?): Set<ExerciseKind>? =
    raw?.mapNotNull { runCatching { ExerciseKind.valueOf(it) }.getOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }

private fun parseDifficulty(raw: String?): DifficultyTier? = when (raw?.uppercase()) {
    "EASY" -> DifficultyTier.BEGINNER
    "MEDIUM" -> DifficultyTier.INTERMEDIATE
    "HARD" -> DifficultyTier.ADVANCED
    else -> null // "ADAPTIVE", null, or unrecognized all mean "let AdaptiveDifficultySelector decide"
}

private fun List<MatchPair>?.toDto(): List<MatchPairDto>? = this?.map { MatchPairDto(it.left, it.right) }

private fun StartSessionResult.toResponse() = SessionPromptResponse(
    sessionRunId = sessionRunId.value.toString(),
    tutoringSessionId = tutoringSessionId.value.toString(),
    promptText = promptText,
    exerciseKind = exerciseKind.name,
    options = options,
    matchPairs = matchPairs.toDto(),
)

private fun SubmitSessionAnswerResult.toResponse() = SubmitSessionAnswerResponse(
    wasCorrect = wasCorrect,
    feedback = feedback,
    nextPrompt = nextPrompt,
    nextTutoringSessionId = nextTutoringSessionId?.value?.toString(),
    sessionCompleted = sessionCompleted,
    summary = summary?.let {
        SessionSummaryDto(
            durationMinutes = it.durationMinutes,
            questionsAsked = it.questionsAsked,
            correctCount = it.correctCount,
            incorrectCount = it.incorrectCount,
            accuracyPercent = it.accuracyPercent,
            wordsMastered = it.wordsMastered,
            wordsNeedingPractice = it.wordsNeedingPractice,
        )
    },
    nextExerciseKind = nextExerciseKind?.name,
    nextOptions = nextOptions,
    nextMatchPairs = nextMatchPairs.toDto(),
)
