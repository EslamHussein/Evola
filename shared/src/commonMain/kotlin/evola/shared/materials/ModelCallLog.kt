package evola.shared.materials

import kotlinx.serialization.Serializable

/** Cheap/JSON-mode vs. premium/reasoning — see the model router in :server (spec §5.3). */
enum class ModelTier { SMALL, LARGE }

enum class TaskType { EXTRACTION, EXERCISE_GENERATION, TAGGING, WRITING_FEEDBACK, SPEAKING_ANALYSIS, COACH_EXPLANATION }

/**
 * One row per model-router call (spec §5.6 observability) — feeds the cache-hit-rate and
 * cost-per-user metrics. A code path for anything on the non-goals list (readiness, mastery
 * scoring, objective grading, scheduling, trend calculation) must never produce one of these.
 */
@Serializable
data class ModelCallLog(
    val id: String,
    val taskType: TaskType,
    val modelTier: ModelTier,
    val inputTokens: Int,
    val outputTokens: Int,
    val costEstimate: Double,
    val cacheHit: Boolean,
    val materialId: String? = null,
    val userId: String? = null,
)
