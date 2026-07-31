package evola.learningresources.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearningResourceId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.AnalyzeResourceRequest
import evola.learningresources.domain.ResourceOverview

data class AnalyzeResourceCommand(val resourceId: LearningResourceId) : Command<DomainResult<ResourceOverview>>

/**
 * Caps the text sent to the AI (see [OVERVIEW_CHAR_LIMIT]) — the port shouldn't own truncation
 * policy, the caller decides what it sends, same as the exercise-generation cost-tiering pattern.
 * Runs once per resource; the overview is then stored directly on the aggregate, never re-generated.
 */
class AnalyzeResourceHandler(
    private val repository: LearningResourceRepository,
    private val aiTutorPort: AiTutorPort,
) : UseCase<AnalyzeResourceCommand, DomainResult<ResourceOverview>> {

    override suspend fun handle(input: AnalyzeResourceCommand): DomainResult<ResourceOverview> {
        val resource = repository.findById(input.resourceId)
            ?: return DomainResult.Err(DomainError.NotFound("Resource not found"))

        val wasTruncated = resource.extractedText.length > OVERVIEW_CHAR_LIMIT
        val textForAnalysis = resource.extractedText.take(OVERVIEW_CHAR_LIMIT)

        return try {
            val result = aiTutorPort.analyzeResource(AnalyzeResourceRequest(textForAnalysis, resource.title))
            val overview = ResourceOverview(
                language = result.language,
                cefrLevel = result.cefrLevel,
                topics = result.topics,
                summary = result.summary,
                modelUsed = result.modelUsed,
                wasTruncated = wasTruncated,
            )
            repository.save(resource.completeAnalysis(overview))
            DomainResult.Ok(overview)
        } catch (e: Exception) {
            repository.save(resource.failAnalysis())
            DomainResult.Err(DomainError.ValidationFailed("Analysis failed: ${e.message}"))
        }
    }

    companion object {
        const val OVERVIEW_CHAR_LIMIT = 20_000
    }
}
