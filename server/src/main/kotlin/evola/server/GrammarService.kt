package evola.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@Serializable
data class GrammarTopicSummaryResponse(
    @SerialName("topic_id") val topicId: String,
    val name: String,
    val explanation: String,
    @SerialName("mastery_state") val masteryState: String,
)

@Serializable
data class GrammarExerciseResponse(
    @SerialName("exercise_id") val exerciseId: String,
    val type: String,
    val prompt: String,
    @SerialName("answer_key") val answerKey: String,
    val choices: List<String> = emptyList(),
    val answered: Boolean = false,
)

@Serializable
data class GrammarSessionResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("topic_name") val topicName: String,
    val exercises: List<GrammarExerciseResponse>,
)

@Serializable
data class GrammarAnswerRequest(
    @SerialName("exercise_id") val exerciseId: String,
    val response: String,
    val correct: Boolean,
)

@Serializable
data class GrammarAnswerResponse(
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("next_review_at") val nextReviewAt: String,
)

@Serializable
data class GrammarSessionCompleteResponse(
    @SerialName("exercises_completed") val exercisesCompleted: Int,
    val accuracy: Double,
)

/**
 * Grammar Learning (M7, 01_PRODUCT_SPEC.md §1.9): a per-topic flat session - the whole exercise
 * list is returned in one `POST`, unlike Vocabulary's pack/7-stage model, matching
 * `03_API_CONTRACT.md`'s Grammar section almost exactly. The client self-grades and reports
 * `correct` (same trust model Vocabulary used pre-redesign) - the exercise payload therefore
 * carries `answer_key`/`choices` so the client can grade fill-in-blank (via the shared
 * `isTolerantMatch`) and multiple-choice locally.
 *
 * Mastery advances only after two consecutive correct answers (see [MasterySrs.onPartialCorrect]);
 * every single answer (right or wrong) immediately updates `grammar_progress` - no "defer to
 * complete" step, unlike Vocabulary's per-pack batching.
 */
class GrammarService(private val database: Database) {

    suspend fun listTopics(userId: String, lessonId: String): List<GrammarTopicSummaryResponse>? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val lessonUuid = UUID.fromString(lessonId)
            val lessonRow = LessonsTable.selectAll().where { LessonsTable.id eq lessonUuid }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (!ownsLesson(userUuid, lessonRow[LessonsTable.goalId])) return@newSuspendedTransaction null

            GrammarTopicsTable
                .join(GrammarProgressTable, JoinType.INNER, GrammarTopicsTable.id, GrammarProgressTable.topicId)
                .selectAll()
                .where { (GrammarTopicsTable.lessonId eq lessonUuid) and (GrammarProgressTable.userId eq userUuid) }
                .map {
                    GrammarTopicSummaryResponse(
                        topicId = it[GrammarTopicsTable.id].toString(),
                        name = it[GrammarTopicsTable.name],
                        explanation = it[GrammarTopicsTable.explanation],
                        masteryState = it[GrammarProgressTable.masteryState],
                    )
                }
        }

    suspend fun startOrResumeSession(userId: String, topicId: String): GrammarSessionResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val topicUuid = UUID.fromString(topicId)
            // Ownership: a grammar_progress row for (user, topic) only exists if this user's own
            // lesson yielded this topic (eagerly inserted alongside the topic at extraction time).
            GrammarProgressTable.selectAll()
                .where { (GrammarProgressTable.userId eq userUuid) and (GrammarProgressTable.topicId eq topicUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null
            val topicRow = GrammarTopicsTable.selectAll().where { GrammarTopicsTable.id eq topicUuid }.singleOrNull()
                ?: return@newSuspendedTransaction null

            val existingSessionId = GrammarSessionsTable.selectAll()
                .where {
                    (GrammarSessionsTable.userId eq userUuid) and
                        (GrammarSessionsTable.topicId eq topicUuid) and
                        GrammarSessionsTable.completedAt.isNull()
                }
                .singleOrNull()?.get(GrammarSessionsTable.id)

            val sessionId = existingSessionId ?: run {
                val newSessionId = UUID.randomUUID()
                GrammarSessionsTable.insert {
                    it[id] = newSessionId
                    it[this.userId] = userUuid
                    it[this.topicId] = topicUuid
                    it[startedAt] = Instant.now()
                    it[completedAt] = null
                    it[accuracy] = null
                }
                newSessionId
            }

            GrammarSessionResponse(
                sessionId = sessionId.toString(),
                topicName = topicRow[GrammarTopicsTable.name],
                exercises = buildExerciseResponses(sessionId, topicUuid),
            )
        }

    /** [correct] is the client's own self-graded verdict (see class doc). Idempotent: a retried
     * POST for an already-answered exercise returns the stored mastery snapshot without
     * re-applying [MasterySrs]. */
    suspend fun answer(userId: String, sessionId: String, request: GrammarAnswerRequest): GrammarAnswerResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val sessionUuid = runCatching { UUID.fromString(sessionId) }.getOrNull() ?: return@newSuspendedTransaction null
            val exerciseUuid = runCatching { UUID.fromString(request.exerciseId) }.getOrNull() ?: return@newSuspendedTransaction null

            val sessionRow = GrammarSessionsTable.selectAll()
                .where { (GrammarSessionsTable.id eq sessionUuid) and (GrammarSessionsTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null
            val topicId = sessionRow[GrammarSessionsTable.topicId]

            val exerciseRow = GrammarExercisesTable.selectAll()
                .where { (GrammarExercisesTable.id eq exerciseUuid) and (GrammarExercisesTable.topicId eq topicId) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val existingAnswer = GrammarSessionAnswersTable.selectAll()
                .where { (GrammarSessionAnswersTable.sessionId eq sessionUuid) and (GrammarSessionAnswersTable.exerciseId eq exerciseUuid) }
                .singleOrNull()
            if (existingAnswer != null) {
                return@newSuspendedTransaction GrammarAnswerResponse(
                    existingAnswer[GrammarSessionAnswersTable.masteryStateAfter],
                    existingAnswer[GrammarSessionAnswersTable.nextReviewAtAfter].toString(),
                )
            }

            val progressRow = GrammarProgressTable.selectAll()
                .where { (GrammarProgressTable.userId eq userUuid) and (GrammarProgressTable.topicId eq topicId) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val currentState = MasterySrs.State(
                masteryState = progressRow[GrammarProgressTable.masteryState],
                intervalIndex = progressRow[GrammarProgressTable.intervalIndex].toInt(),
                correctStreak = progressRow[GrammarProgressTable.correctStreak],
            )
            // Two-consecutive-correct advancement rule: even correctStreak -> first of a new pair
            // (partial only), odd -> second of the pair (real onCorrect, mastery advances).
            val nextState = when {
                !request.correct -> MasterySrs.onIncorrect(currentState)
                currentState.correctStreak % 2 == 0 -> MasterySrs.onPartialCorrect(currentState)
                else -> MasterySrs.onCorrect(currentState)
            }
            val now = Instant.now()
            val nextReviewAt = now.plus(MasterySrs.intervalDaysFor(nextState.intervalIndex), ChronoUnit.DAYS)

            GrammarProgressTable.update({
                (GrammarProgressTable.userId eq userUuid) and (GrammarProgressTable.topicId eq topicId)
            }) {
                it[masteryState] = nextState.masteryState
                it[intervalIndex] = nextState.intervalIndex.toShort()
                it[correctStreak] = nextState.correctStreak
                it[this.nextReviewAt] = nextReviewAt
                it[lastReviewedAt] = now
            }

            GrammarSessionAnswersTable.insert {
                it[id] = UUID.randomUUID()
                it[this.sessionId] = sessionUuid
                it[this.exerciseId] = exerciseUuid
                it[response] = request.response
                it[correct] = request.correct
                it[masteryStateAfter] = nextState.masteryState
                it[nextReviewAtAfter] = nextReviewAt
                it[answeredAt] = now
            }

            GrammarAnswerResponse(nextState.masteryState, nextReviewAt.toString())
        }

    suspend fun complete(userId: String, sessionId: String, localDate: String? = null): GrammarSessionCompleteResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val sessionUuid = runCatching { UUID.fromString(sessionId) }.getOrNull() ?: return@newSuspendedTransaction null
            GrammarSessionsTable.selectAll()
                .where { (GrammarSessionsTable.id eq sessionUuid) and (GrammarSessionsTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val answers = GrammarSessionAnswersTable.selectAll()
                .where { GrammarSessionAnswersTable.sessionId eq sessionUuid }
                .toList()
            val exercisesCompleted = answers.size
            val correctCount = answers.count { it[GrammarSessionAnswersTable.correct] }
            val accuracy = if (exercisesCompleted > 0) (correctCount.toDouble() / exercisesCompleted) * 100.0 else 0.0

            GrammarSessionsTable.update({ GrammarSessionsTable.id eq sessionUuid }) {
                it[completedAt] = Instant.now()
                it[this.accuracy] = BigDecimal(accuracy).setScale(2, RoundingMode.HALF_UP)
            }

            recordDailyActivity(userUuid, resolveLocalDate(localDate))

            GrammarSessionCompleteResponse(exercisesCompleted, accuracy)
        }

    private fun ownsLesson(userId: UUID, goalId: UUID): Boolean =
        GoalsTable.selectAll().where { (GoalsTable.id eq goalId) and (GoalsTable.userId eq userId) }.any()

    /** This topic's exercise set is immutable after generation (no snapshot/junction table) - "this
     * session's exercises" is always derived as grammar_exercises WHERE topic_id = topicId, left-
     * joined against this session's own answers to mark already-graded ones and skip a reshuffle.
     * Multiple-choice choices are shuffled deterministically, seeded by (sessionId+exerciseId), so
     * a resumed session shows identical choice order every time. */
    private fun buildExerciseResponses(sessionId: UUID, topicId: UUID): List<GrammarExerciseResponse> {
        val answeredIds = GrammarSessionAnswersTable.selectAll()
            .where { GrammarSessionAnswersTable.sessionId eq sessionId }
            .map { it[GrammarSessionAnswersTable.exerciseId] }
            .toSet()

        return GrammarExercisesTable.selectAll()
            .where { GrammarExercisesTable.topicId eq topicId }
            .orderBy(GrammarExercisesTable.createdAt, SortOrder.ASC)
            .map { it.toExerciseResponse(sessionId, answeredIds) }
    }

    private fun ResultRow.toExerciseResponse(sessionId: UUID, answeredIds: Set<UUID>): GrammarExerciseResponse {
        val exerciseId = this[GrammarExercisesTable.id]
        val type = this[GrammarExercisesTable.type]
        val answerKey = this[GrammarExercisesTable.answerKey]
        val distractors = this[GrammarExercisesTable.distractors]
            ?.let { runCatching { MATERIALS_JSON.decodeFromString(ListSerializer(String.serializer()), it) }.getOrDefault(emptyList()) }
            ?: emptyList()

        val choices = if (type == "multiple_choice" && distractors.isNotEmpty()) {
            (distractors + answerKey).shuffled(Random(sessionId.hashCode() + exerciseId.hashCode()))
        } else {
            emptyList()
        }

        return GrammarExerciseResponse(
            exerciseId = exerciseId.toString(),
            type = type,
            prompt = this[GrammarExercisesTable.prompt],
            answerKey = answerKey,
            choices = choices,
            answered = exerciseId in answeredIds,
        )
    }
}
