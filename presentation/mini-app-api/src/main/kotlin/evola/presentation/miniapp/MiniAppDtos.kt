package evola.presentation.miniapp

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class StartSessionRequest(
    val budgetType: String,
    val budgetValue: Int,
    val allowedKinds: List<String>? = null,
    val difficulty: String? = null,
)

@Serializable
data class MatchPairDto(val left: String, val right: String)

@Serializable
data class SessionPromptResponse(
    val sessionRunId: String,
    val tutoringSessionId: String,
    val promptText: String,
    val exerciseKind: String,
    val options: List<String>? = null,
    val matchPairs: List<MatchPairDto>? = null,
)

@Serializable
data class SubmitSessionAnswerRequest(
    val sessionRunId: String,
    val tutoringSessionId: String,
    val rawAnswer: String,
)

@Serializable
data class SessionSummaryDto(
    val durationMinutes: Long,
    val questionsAsked: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val accuracyPercent: Int,
    val wordsMastered: Int,
    val wordsNeedingPractice: Int,
)

@Serializable
data class SubmitSessionAnswerResponse(
    val wasCorrect: Boolean,
    val feedback: String,
    val nextPrompt: String? = null,
    val nextTutoringSessionId: String? = null,
    val sessionCompleted: Boolean,
    val summary: SessionSummaryDto? = null,
    val nextExerciseKind: String? = null,
    val nextOptions: List<String>? = null,
    val nextMatchPairs: List<MatchPairDto>? = null,
)

@Serializable
data class SetModeRequest(val mode: String)
