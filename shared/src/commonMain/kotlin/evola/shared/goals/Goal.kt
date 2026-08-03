package evola.shared.goals

/** Onboarding + Goal Setup per 01_PRODUCT_SPEC.md §1.3-1.4: exactly one active goal per account. */
data class Goal(
    val id: String,
    val goalText: String,
    val title: String?,
    val isActive: Boolean,
    val createdAt: String,
)

sealed interface CreateGoalResult {
    data class Success(val goal: Goal) : CreateGoalResult
    data object ActiveGoalExists : CreateGoalResult
    data class ValidationError(val message: String) : CreateGoalResult
}

sealed interface UpdateGoalResult {
    data class Success(val goal: Goal) : UpdateGoalResult
    data object NotFound : UpdateGoalResult
    data class ValidationError(val message: String) : UpdateGoalResult
}

/** Lesson Selection (01_PRODUCT_SPEC.md §1.7) - status mirrors the DB's `lesson_status` enum
 * ("pending" | "ready" | "failed"). vocabProgress/grammarProgress are always 0 until M6/M7
 * populate real vocabulary_items/grammar_topics - an honest placeholder, not a fake number. */
data class Lesson(
    val id: String,
    val number: Int,
    val title: String,
    val status: String,
    val vocabProgress: Float,
    val grammarProgress: Float,
) {
    /** Per spec: average of vocab/grammar progress, or vocab alone when the lesson has no grammar
     * topics yet (grammarProgress == 0 is indistinguishable from "no topics" at MVP, matching the
     * spec's own explicit simplification). */
    val completionPct: Float get() = if (grammarProgress == 0f) vocabProgress else (vocabProgress + grammarProgress) / 2f
    val isReady: Boolean get() = status == "ready"
}

interface GoalsRepository {
    suspend fun createGoal(accessToken: String, goalText: String, title: String?): CreateGoalResult
    suspend fun updateGoal(accessToken: String, goalId: String, goalText: String?, title: String?): UpdateGoalResult
    suspend fun getActiveGoal(accessToken: String): Goal?
    suspend fun listLessons(accessToken: String, goalId: String): List<Lesson>
}
