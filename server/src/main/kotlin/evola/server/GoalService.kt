package evola.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

@Serializable
data class CreateGoalRequest(@SerialName("goal_text") val goalText: String, val title: String? = null)

@Serializable
data class UpdateGoalRequest(@SerialName("goal_text") val goalText: String? = null, val title: String? = null)

@Serializable
data class GoalResponse(
    val id: String,
    @SerialName("goal_text") val goalText: String,
    val title: String?,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LessonSummaryResponse(
    @SerialName("lesson_id") val lessonId: String,
    val number: Int,
    val title: String,
    val status: String,
    @SerialName("vocab_progress") val vocabProgress: Float = 0f,
    @SerialName("grammar_progress") val grammarProgress: Float = 0f,
    @SerialName("grammar_count") val grammarCount: Int = 0,
)

sealed interface CreateGoalOutcome {
    data class Created(val goal: GoalResponse) : CreateGoalOutcome
    data object ActiveGoalExists : CreateGoalOutcome
    data class Invalid(val message: String) : CreateGoalOutcome
}

sealed interface UpdateGoalOutcome {
    data class Updated(val goal: GoalResponse) : UpdateGoalOutcome
    data object NotFound : UpdateGoalOutcome
    data class Invalid(val message: String) : UpdateGoalOutcome
}

private const val TITLE_WORD_COUNT = 5

/**
 * Onboarding + Goal Setup per 01_PRODUCT_SPEC.md §1.3-1.4: exactly one active goal per account at
 * MVP (enforced by the DB's partial unique index, not just this check-then-insert), freeform goal
 * text, auto-titled when blank. `users.onboarding_completed` only flips true here, as a side
 * effect of the first successful goal creation - the client's own navigation (Welcome must be
 * shown before Goal Setup is reachable) is what guarantees Welcome was seen, so no separate
 * "welcome_seen" column is needed to satisfy "flips true only once both steps are done."
 */
class GoalService(private val database: Database) {

    private fun isValidGoalText(text: String): Boolean = text.length in 3..200

    private fun isValidTitle(title: String?): Boolean = title == null || title.length <= 60

    private fun autoTitle(goalText: String): String {
        val words = goalText.trim().split(Regex("\\s+")).take(TITLE_WORD_COUNT).joinToString(" ")
        val candidate = "My $words Journey"
        return if (candidate.length <= 60) candidate else candidate.take(60)
    }

    suspend fun createGoal(userId: String, request: CreateGoalRequest): CreateGoalOutcome {
        val goalText = request.goalText.trim()
        val title = request.title?.trim()?.takeIf { it.isNotEmpty() }

        if (!isValidGoalText(goalText)) return CreateGoalOutcome.Invalid("Goal text must be 3-200 characters.")
        if (!isValidTitle(title)) return CreateGoalOutcome.Invalid("Title must be 60 characters or fewer.")

        return newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val goalId = UUID.randomUUID()
            val now = Instant.now()
            val resolvedTitle = title ?: autoTitle(goalText)

            try {
                GoalsTable.insert {
                    it[id] = goalId
                    it[this.userId] = userUuid
                    it[this.goalText] = goalText
                    it[this.title] = resolvedTitle
                    it[isActive] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } catch (e: ExposedSQLException) {
                if (isUniqueViolation(e)) return@newSuspendedTransaction CreateGoalOutcome.ActiveGoalExists
                throw e
            }

            UsersTable.update({ UsersTable.id eq userUuid }) {
                it[onboardingCompleted] = true
                it[updatedAt] = now
            }

            CreateGoalOutcome.Created(
                GoalResponse(goalId.toString(), goalText, resolvedTitle, isActive = true, createdAt = now.toString()),
            )
        }
    }

    suspend fun updateGoal(userId: String, goalId: String, request: UpdateGoalRequest): UpdateGoalOutcome {
        val goalText = request.goalText?.trim()
        val title = request.title?.trim()

        if (goalText != null && !isValidGoalText(goalText)) {
            return UpdateGoalOutcome.Invalid("Goal text must be 3-200 characters.")
        }
        if (title != null && !isValidTitle(title)) {
            return UpdateGoalOutcome.Invalid("Title must be 60 characters or fewer.")
        }

        return newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val goalUuid = UUID.fromString(goalId)
            val existing = GoalsTable.selectAll()
                .where { (GoalsTable.id eq goalUuid) and (GoalsTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction UpdateGoalOutcome.NotFound

            val now = Instant.now()
            GoalsTable.update({ GoalsTable.id eq goalUuid }) {
                if (goalText != null) it[this.goalText] = goalText
                if (title != null) it[this.title] = title
                it[updatedAt] = now
            }

            UpdateGoalOutcome.Updated(
                GoalResponse(
                    id = goalId,
                    goalText = goalText ?: existing[GoalsTable.goalText],
                    title = title ?: existing[GoalsTable.title],
                    isActive = existing[GoalsTable.isActive],
                    createdAt = existing[GoalsTable.createdAt].toString(),
                ),
            )
        }
    }

    suspend fun getGoal(userId: String, goalId: String): GoalResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            GoalsTable.selectAll()
                .where { (GoalsTable.id eq UUID.fromString(goalId)) and (GoalsTable.userId eq UUID.fromString(userId)) }
                .singleOrNull()?.toGoalResponse()
        }

    suspend fun getActiveGoal(userId: String): GoalResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            GoalsTable.selectAll()
                .where { (GoalsTable.userId eq UUID.fromString(userId)) and (GoalsTable.isActive eq true) }
                .singleOrNull()?.toGoalResponse()
        }

    /** M5 Lesson Selection (01_PRODUCT_SPEC.md §1.7, 03_API_CONTRACT.md "GET /goals/{id}/lessons"):
     * all lessons across all of this goal's materials, in sequential order - ordering by
     * created_at (then number as a tie-break) naturally gives "grouped by source, appended at the
     * end" for a later-uploaded material without needing to join materials. vocab_progress/
     * grammar_progress are always 0 until M6/M7 populate real vocabulary_items/grammar_topics -
     * an honest placeholder, not a fake number. Returns null when the goal isn't the caller's. */
    suspend fun listLessonsForGoal(userId: String, goalId: String): List<LessonSummaryResponse>? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val goalUuid = UUID.fromString(goalId)
            val ownsGoal = GoalsTable.selectAll()
                .where { (GoalsTable.id eq goalUuid) and (GoalsTable.userId eq userUuid) }
                .any()
            if (!ownsGoal) return@newSuspendedTransaction null

            LessonsTable.selectAll()
                .where { LessonsTable.goalId eq goalUuid }
                .orderBy(LessonsTable.createdAt to SortOrder.ASC, LessonsTable.number to SortOrder.ASC)
                .map { it.toLessonSummary(userUuid) }
        }

    /** Same vocab/grammar-progress computation MaterialService.toLesson uses, against this
     * lesson's own items/topics for the given user - kept in sync with that one deliberately,
     * per the "structurally identical progress tables" shared-module convention (01_PRODUCT_SPEC
     * §2.1). Not yet extracted into one shared helper - both call sites are still simple enough
     * that the duplication is cheap to keep matching, not worth a premature abstraction. */
    private fun ResultRow.toLessonSummary(userId: UUID): LessonSummaryResponse {
        val lessonId = this[LessonsTable.id]

        val vocabItemIds = VocabularyItemsTable.selectAll()
            .where { VocabularyItemsTable.lessonId eq lessonId }
            .map { it[VocabularyItemsTable.id] }
        val vocabProgress = if (vocabItemIds.isEmpty()) {
            0f
        } else {
            val stageIndices = VocabularyProgressTable.selectAll()
                .where { (VocabularyProgressTable.userId eq userId) and (VocabularyProgressTable.vocabularyItemId inList vocabItemIds) }
                .map { MasterySrs.STAGES.indexOf(it[VocabularyProgressTable.masteryState]).coerceAtLeast(0) }
            if (stageIndices.isEmpty()) 0f else stageIndices.map { it / (MasterySrs.STAGES.size - 1f) }.average().toFloat()
        }

        val topicIds = GrammarTopicsTable.selectAll()
            .where { GrammarTopicsTable.lessonId eq lessonId }
            .map { it[GrammarTopicsTable.id] }
        val grammarProgress = if (topicIds.isEmpty()) {
            0f
        } else {
            val stageIndices = GrammarProgressTable.selectAll()
                .where { (GrammarProgressTable.userId eq userId) and (GrammarProgressTable.topicId inList topicIds) }
                .map { MasterySrs.STAGES.indexOf(it[GrammarProgressTable.masteryState]).coerceAtLeast(0) }
            if (stageIndices.isEmpty()) 0f else stageIndices.map { it / (MasterySrs.STAGES.size - 1f) }.average().toFloat()
        }

        return LessonSummaryResponse(
            lessonId = lessonId.toString(),
            number = this[LessonsTable.number],
            title = this[LessonsTable.title],
            status = this[LessonsTable.status],
            vocabProgress = vocabProgress,
            grammarProgress = grammarProgress,
            grammarCount = topicIds.size,
        )
    }

    private fun ResultRow.toGoalResponse() = GoalResponse(
        id = this[GoalsTable.id].toString(),
        goalText = this[GoalsTable.goalText],
        title = this[GoalsTable.title],
        isActive = this[GoalsTable.isActive],
        createdAt = this[GoalsTable.createdAt].toString(),
    )

    private fun isUniqueViolation(e: ExposedSQLException): Boolean =
        (e.cause as? SQLException)?.sqlState == "23505"
}
