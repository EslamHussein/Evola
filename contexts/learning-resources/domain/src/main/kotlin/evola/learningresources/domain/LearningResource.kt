package evola.learningresources.domain

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningResourceId
import java.time.Instant

enum class SourceType { PDF, TEXT_NOTE }
// .docx intentionally not added yet — extension seam: one enum case + one extractor branch later.

enum class ResourceStatus { UPLOADED, ANALYZED, ANALYSIS_FAILED }

data class ResourceOverview(
    val language: String,
    val cefrLevel: String,
    val topics: List<String>,
    val summary: String,
    val modelUsed: String,
    val wasTruncated: Boolean,
)

/**
 * A learner's uploaded material (PDF or pasted text). Goal selection is deliberately NOT stored
 * on the aggregate — the same resource can be revisited later with a different focus (vocabulary,
 * grammar, speaking, exam prep), so "what am I practicing right now" lives only in the ephemeral
 * request that generates content, not as resource state.
 */
data class LearningResource(
    val id: LearningResourceId,
    val learnerId: LearnerId,
    val title: String,
    val sourceType: SourceType,
    val storagePath: String?,
    val extractedText: String,
    val status: ResourceStatus,
    val overview: ResourceOverview?,
    val createdAt: Instant,
) {
    companion object {
        fun upload(
            id: LearningResourceId,
            learnerId: LearnerId,
            title: String,
            sourceType: SourceType,
            storagePath: String?,
            extractedText: String,
            now: Instant = Instant.now(),
        ) = LearningResource(
            id = id,
            learnerId = learnerId,
            title = title,
            sourceType = sourceType,
            storagePath = storagePath,
            extractedText = extractedText,
            status = ResourceStatus.UPLOADED,
            overview = null,
            createdAt = now,
        )
    }

    fun completeAnalysis(overview: ResourceOverview) = copy(status = ResourceStatus.ANALYZED, overview = overview)

    fun failAnalysis() = copy(status = ResourceStatus.ANALYSIS_FAILED)
}
