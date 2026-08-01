package evola.shared.materials

import kotlinx.serialization.Serializable

enum class MaterialStatus { UPLOADED, ANALYZING, ANALYZED, NEEDS_REVIEW, FAILED }

@Serializable
data class Material(
    val id: String,
    val userId: String,
    val filename: String,
    val contentHash: String,
    val extractionCacheId: String? = null,
    val status: MaterialStatus,
)

@Serializable
data class VocabItem(val term: String, val translation: String, val exampleSentence: String? = null)

@Serializable
data class GrammarRule(val topic: String, val explanation: String)

@Serializable
data class Exercise(val kind: String, val prompt: String, val answerKey: String)

/**
 * Shared across all users by contentHash — the Nth person to upload a popular textbook costs
 * nothing to extract (see ExtractionJob / the content-hash dedup cache in :server).
 */
@Serializable
data class ExtractionCache(
    val id: String,
    val contentHash: String,
    val vocabulary: List<VocabItem>,
    val grammar: List<GrammarRule>,
    val exercises: List<Exercise>,
    val confidence: Float,
    val modelVersion: String,
)

enum class JobStatus { QUEUED, PROCESSING, DONE, NEEDS_REVIEW, FAILED }

@Serializable
data class ExtractionJob(
    val id: String,
    val contentHash: String,
    val status: JobStatus,
    val error: String? = null,
)

@Serializable
data class MaterialDetail(
    val material: Material,
    val extraction: ExtractionCache?,
)
