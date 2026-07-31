package evola.learningresources.application

import evola.core.application.Query
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearningResourceId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.GenerateLearningContentRequest
import evola.integrations.aigateway.GeneratedLearningContent
import evola.learningresources.domain.ResourceStatus

data class GenerateResourceContentQuery(val resourceId: LearningResourceId, val goal: String) :
    Query<DomainResult<GeneratedLearningContent>>

/**
 * Cache-through, mirroring exercise-generation's GenerateExerciseForWordHandler: the same
 * (resource, goal) combination never triggers a second LLM call — a learner can revisit the same
 * resource with a different goal later without invalidating what's already cached.
 */
class GenerateResourceContentHandler(
    private val repository: LearningResourceRepository,
    private val cache: LearningResourceContentCache,
    private val aiTutorPort: AiTutorPort,
) : UseCase<GenerateResourceContentQuery, DomainResult<GeneratedLearningContent>> {

    override suspend fun handle(input: GenerateResourceContentQuery): DomainResult<GeneratedLearningContent> {
        val resource = repository.findById(input.resourceId)
            ?: return DomainResult.Err(DomainError.NotFound("Resource not found"))
        if (resource.status != ResourceStatus.ANALYZED) {
            return DomainResult.Err(DomainError.ValidationFailed("Resource has not been analyzed yet"))
        }

        cache.find(input.resourceId, input.goal)?.let { return DomainResult.Ok(it) }

        val generated = aiTutorPort.generateLearningContent(
            GenerateLearningContentRequest(
                text = resource.extractedText.take(GENERATION_CHAR_LIMIT),
                goal = input.goal,
                expectedLevel = resource.overview?.cefrLevel ?: "UNKNOWN",
                topics = resource.overview?.topics.orEmpty(),
            ),
        )
        cache.store(input.resourceId, input.goal, generated)
        return DomainResult.Ok(generated)
    }

    companion object {
        const val GENERATION_CHAR_LIMIT = 40_000
    }
}
