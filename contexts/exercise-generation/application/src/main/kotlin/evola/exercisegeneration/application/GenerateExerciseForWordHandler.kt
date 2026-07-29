package evola.exercisegeneration.application

import evola.core.application.Query
import evola.core.application.UseCase
import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.ExerciseType
import evola.integrations.aigateway.GenerateExerciseRequest
import evola.integrations.aigateway.GeneratedExercise

data class GenerateExerciseForWordQuery(
    val vocabularyItemId: VocabularyItemId,
    val germanWord: String,
    val englishTranslation: String,
    val cefrLevel: String,
    val exerciseType: ExerciseType = ExerciseType.EXAMPLE_SENTENCE,
) : Query<GeneratedExercise>

/**
 * Cache-through: the same word never triggers a second LLM call, for any learner (see
 * ADR-0001-mvp cost-reduction principles). Not pure CQRS — a Query with a caching side effect —
 * a deliberate, named exception rather than an accidental leak.
 */
class GenerateExerciseForWordHandler(
    private val cache: ExerciseCache,
    private val aiTutorPort: AiTutorPort,
) : UseCase<GenerateExerciseForWordQuery, GeneratedExercise> {

    override suspend fun handle(input: GenerateExerciseForWordQuery): GeneratedExercise {
        cache.find(input.vocabularyItemId, input.exerciseType)?.let { return it }

        val generated = aiTutorPort.generateExercise(
            GenerateExerciseRequest(
                germanWord = input.germanWord,
                englishTranslation = input.englishTranslation,
                cefrLevel = input.cefrLevel,
                exerciseType = input.exerciseType,
            ),
        )
        cache.store(input.vocabularyItemId, input.exerciseType, generated)
        return generated
    }
}
