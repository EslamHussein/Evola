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
    val grammarCount: Int = 0,
) {
    /** Per 01_PRODUCT_SPEC.md §1.7: average of vocab/grammar progress, or vocab alone when the
     * lesson has no grammar topics yet - branches on grammarCount (a real topic count), not on
     * grammarProgress == 0f (a real bug found during M7 planning: that check is indistinguishable
     * from "a real topic sitting at 0% mastery," harmless only while grammarProgress was always
     * hardcoded 0 before M7). */
    val completionPct: Float get() = if (grammarCount == 0) vocabProgress else (vocabProgress + grammarProgress) / 2f
    val isReady: Boolean get() = status == "ready"
}

/** Progress Dashboard aggregate (01_PRODUCT_SPEC.md §1.10). [currentLessonId] is the first lesson
 * still below 100% (null once every lesson is done, or there are none yet); the client resolves it
 * against its own already-fetched lesson list to build the "Continue Lesson N" CTA. */
data class GoalProgress(
    val overallPct: Float,
    val currentLessonId: String?,
    val streakDays: Int,
    val todayCompleted: Boolean,
)

interface GoalsRepository {
    suspend fun createGoal(accessToken: String, goalText: String, title: String?): CreateGoalResult
    suspend fun updateGoal(accessToken: String, goalId: String, goalText: String?, title: String?): UpdateGoalResult
    suspend fun getActiveGoal(accessToken: String): Goal?
    suspend fun listLessons(accessToken: String, goalId: String): List<Lesson>
    suspend fun getProgress(accessToken: String, goalId: String, localDate: String): GoalProgress?
}
