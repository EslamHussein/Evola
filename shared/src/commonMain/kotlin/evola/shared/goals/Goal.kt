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

interface GoalsRepository {
    suspend fun createGoal(accessToken: String, goalText: String, title: String?): CreateGoalResult
    suspend fun updateGoal(accessToken: String, goalId: String, goalText: String?, title: String?): UpdateGoalResult
    suspend fun getActiveGoal(accessToken: String): Goal?
}
