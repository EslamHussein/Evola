package evola.integrations.aigateway

enum class ExerciseType { EXAMPLE_SENTENCE }

data class GenerateExerciseRequest(
    val germanWord: String,
    val englishTranslation: String,
    val cefrLevel: String,
    val exerciseType: ExerciseType,
)

data class GeneratedExercise(
    val content: String,
    val modelUsed: String,
)

/**
 * Shared AI capability boundary — not owned by any single bounded context. Intent-shaped
 * (`generateExercise`, not "send this prompt") so prompt design, model choice, and provider
 * stay confined to this integration module (see ADR D3).
 */
interface AiTutorPort {
    suspend fun generateExercise(request: GenerateExerciseRequest): GeneratedExercise
}
