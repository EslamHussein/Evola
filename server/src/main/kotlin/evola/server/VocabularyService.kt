package evola.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
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

private const val NEW_ITEMS_CAP = 12
private const val DUE_REVIEW_CAP = 15
private const val MASTERED_FALLBACK_CAP = 5
private const val RESURFACE_OFFSET = 3

@Serializable
data class VocabularyItemResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("mastery_state") val masteryState: String,
)

@Serializable
data class VocabularySessionItemResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    @SerialName("drill_type") val drillType: String,
    val choices: List<String> = emptyList(),
)

@Serializable
data class VocabularySessionResponse(
    @SerialName("session_id") val sessionId: String,
    val items: List<VocabularySessionItemResponse>,
    @SerialName("has_lesson_vocabulary") val hasLessonVocabulary: Boolean,
)

@Serializable
data class VocabularyAnswerRequest(
    @SerialName("item_id") val itemId: String,
    val response: String,
    val correct: Boolean,
)

@Serializable
data class VocabularyAnswerResponse(
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("next_review_at") val nextReviewAt: String,
    val resurfaced: Boolean,
)

@Serializable
data class VocabularySessionCompleteResponse(
    @SerialName("items_count") val itemsCount: Int,
    val accuracy: Double,
)

private data class AssembledItem(val vocabularyItemId: UUID, val drillType: String, val choices: List<String>?)

/**
 * Vocabulary Learning (01_PRODUCT_SPEC.md §1.8): session assembly/resume, answer grading via
 * [MasterySrs], and the per-lesson vocabulary list. `POST /lessons/{id}/vocabulary/session` resumes
 * an existing incomplete session instead of creating a new one whenever possible - that's the
 * whole implementation of "exits mid-session -> resumable, not restarted," no separate endpoint
 * needed. Ownership is always lesson -> goal -> user, since vocabulary_items has no direct user_id.
 */
class VocabularyService(private val database: Database) {

    suspend fun startOrResumeSession(userId: String, lessonId: String): VocabularySessionResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val lessonUuid = UUID.fromString(lessonId)
            val lessonRow = LessonsTable.selectAll().where { LessonsTable.id eq lessonUuid }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (!ownsLesson(userUuid, lessonRow[LessonsTable.goalId])) return@newSuspendedTransaction null

            val existingSessionId = VocabularySessionsTable.selectAll()
                .where {
                    (VocabularySessionsTable.userId eq userUuid) and
                        (VocabularySessionsTable.lessonId eq lessonUuid) and
                        VocabularySessionsTable.completedAt.isNull()
                }
                .singleOrNull()?.get(VocabularySessionsTable.id)

            val sessionId = existingSessionId ?: run {
                val newSessionId = UUID.randomUUID()
                val assembled = assembleSessionItems(userUuid, lessonUuid)
                VocabularySessionsTable.insert {
                    it[id] = newSessionId
                    it[this.userId] = userUuid
                    it[this.lessonId] = lessonUuid
                    it[startedAt] = Instant.now()
                    it[itemsCount] = assembled.size
                }
                assembled.forEachIndexed { index, item -> insertSessionItem(newSessionId, item, index) }
                newSessionId
            }

            val hasLessonVocabulary = VocabularyItemsTable.selectAll().where { VocabularyItemsTable.lessonId eq lessonUuid }.any()
            VocabularySessionResponse(sessionId.toString(), loadUnansweredSessionItems(sessionId), hasLessonVocabulary)
        }

    suspend fun listVocabulary(userId: String, lessonId: String): List<VocabularyItemResponse>? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val lessonUuid = UUID.fromString(lessonId)
            val lessonRow = LessonsTable.selectAll().where { LessonsTable.id eq lessonUuid }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (!ownsLesson(userUuid, lessonRow[LessonsTable.goalId])) return@newSuspendedTransaction null

            VocabularyItemsTable
                .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
                .selectAll()
                .where { (VocabularyItemsTable.lessonId eq lessonUuid) and (VocabularyProgressTable.userId eq userUuid) }
                .map {
                    VocabularyItemResponse(
                        itemId = it[VocabularyItemsTable.id].toString(),
                        term = it[VocabularyItemsTable.term],
                        meaning = it[VocabularyItemsTable.meaning],
                        gender = it[VocabularyItemsTable.gender],
                        exampleSentence = it[VocabularyItemsTable.exampleSentence],
                        masteryState = it[VocabularyProgressTable.masteryState],
                    )
                }
        }

    suspend fun answer(userId: String, sessionId: String, request: VocabularyAnswerRequest): VocabularyAnswerResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val sessionUuid = UUID.fromString(sessionId)
            val sessionItemUuid = runCatching { UUID.fromString(request.itemId) }.getOrNull()
                ?: return@newSuspendedTransaction null

            VocabularySessionsTable.selectAll()
                .where { (VocabularySessionsTable.id eq sessionUuid) and (VocabularySessionsTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val sessionItemRow = VocabularySessionItemsTable.selectAll()
                .where { (VocabularySessionItemsTable.id eq sessionItemUuid) and (VocabularySessionItemsTable.sessionId eq sessionUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null
            val vocabularyItemId = sessionItemRow[VocabularySessionItemsTable.vocabularyItemId]

            VocabularySessionItemsTable.update({ VocabularySessionItemsTable.id eq sessionItemUuid }) {
                it[answeredCorrectly] = request.correct
                it[answeredAt] = Instant.now()
            }

            val progressRow = VocabularyProgressTable.selectAll()
                .where { (VocabularyProgressTable.userId eq userUuid) and (VocabularyProgressTable.vocabularyItemId eq vocabularyItemId) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val currentState = MasterySrs.State(
                masteryState = progressRow[VocabularyProgressTable.masteryState],
                intervalIndex = progressRow[VocabularyProgressTable.intervalIndex].toInt(),
                correctStreak = progressRow[VocabularyProgressTable.correctStreak],
            )
            val nextState = if (request.correct) MasterySrs.onCorrect(currentState) else MasterySrs.onIncorrect(currentState)
            val now = Instant.now()
            val nextReviewAt = now.plus(MasterySrs.intervalDaysFor(nextState.intervalIndex), ChronoUnit.DAYS)

            VocabularyProgressTable.update({
                (VocabularyProgressTable.userId eq userUuid) and (VocabularyProgressTable.vocabularyItemId eq vocabularyItemId)
            }) {
                it[masteryState] = nextState.masteryState
                it[intervalIndex] = nextState.intervalIndex.toShort()
                it[correctStreak] = nextState.correctStreak
                it[this.nextReviewAt] = nextReviewAt
                it[lastReviewedAt] = now
            }

            // "Same item wrong twice... resurfaced later in the session, not immediately" (spec
            // §1.8) - insert one new occurrence a few positions later rather than replacing this one.
            val resurfaced = if (!request.correct) {
                val maxPosition = VocabularySessionItemsTable.selectAll()
                    .where { VocabularySessionItemsTable.sessionId eq sessionUuid }
                    .maxOf { it[VocabularySessionItemsTable.position] }
                VocabularySessionItemsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.sessionId] = sessionUuid
                    it[this.vocabularyItemId] = vocabularyItemId
                    it[position] = maxPosition + RESURFACE_OFFSET
                    it[drillType] = sessionItemRow[VocabularySessionItemsTable.drillType]
                    it[choices] = sessionItemRow[VocabularySessionItemsTable.choices]
                }
                true
            } else {
                false
            }

            VocabularyAnswerResponse(nextState.masteryState, nextReviewAt.toString(), resurfaced)
        }

    suspend fun complete(userId: String, sessionId: String): VocabularySessionCompleteResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val sessionUuid = UUID.fromString(sessionId)
            VocabularySessionsTable.selectAll()
                .where { (VocabularySessionsTable.id eq sessionUuid) and (VocabularySessionsTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val answered = VocabularySessionItemsTable.selectAll()
                .where { (VocabularySessionItemsTable.sessionId eq sessionUuid) and VocabularySessionItemsTable.answeredCorrectly.isNotNull() }
                .toList()
            val itemsCount = answered.size
            val correctCount = answered.count { it[VocabularySessionItemsTable.answeredCorrectly] == true }
            val accuracy = if (itemsCount > 0) (correctCount.toDouble() / itemsCount) * 100.0 else 0.0

            VocabularySessionsTable.update({ VocabularySessionsTable.id eq sessionUuid }) {
                it[completedAt] = Instant.now()
                it[this.itemsCount] = itemsCount
                it[this.accuracy] = BigDecimal(accuracy).setScale(2, RoundingMode.HALF_UP)
            }

            VocabularySessionCompleteResponse(itemsCount, accuracy)
        }

    private fun ownsLesson(userId: UUID, goalId: UUID): Boolean =
        GoalsTable.selectAll().where { (GoalsTable.id eq goalId) and (GoalsTable.userId eq userId) }.any()

    private fun insertSessionItem(sessionId: UUID, item: AssembledItem, position: Int) {
        VocabularySessionItemsTable.insert {
            it[id] = UUID.randomUUID()
            it[this.sessionId] = sessionId
            it[vocabularyItemId] = item.vocabularyItemId
            it[this.position] = position
            it[drillType] = item.drillType
            it[choices] = item.choices?.let { c -> MATERIALS_JSON.encodeToString(ListSerializer(String.serializer()), c) }
        }
    }

    private fun loadUnansweredSessionItems(sessionId: UUID): List<VocabularySessionItemResponse> =
        VocabularySessionItemsTable
            .join(VocabularyItemsTable, JoinType.INNER, VocabularySessionItemsTable.vocabularyItemId, VocabularyItemsTable.id)
            .selectAll()
            .where { (VocabularySessionItemsTable.sessionId eq sessionId) and VocabularySessionItemsTable.answeredCorrectly.isNull() }
            .orderBy(VocabularySessionItemsTable.position, SortOrder.ASC)
            .map {
                val choicesJson = it[VocabularySessionItemsTable.choices]
                val choices = choicesJson?.let { c -> MATERIALS_JSON.decodeFromString(ListSerializer(String.serializer()), c) } ?: emptyList()
                VocabularySessionItemResponse(
                    itemId = it[VocabularySessionItemsTable.id].toString(),
                    term = it[VocabularyItemsTable.term],
                    meaning = it[VocabularyItemsTable.meaning],
                    drillType = it[VocabularySessionItemsTable.drillType],
                    choices = choices,
                )
            }

    /** New words from this lesson (cap [NEW_ITEMS_CAP]) mixed with due review words from other
     * lessons (cap [DUE_REVIEW_CAP], only items already studied at least once - `next_review_at`
     * defaults to "now" at creation, so `last_reviewed_at IS NOT NULL` is what actually keeps
     * never-studied items from other lessons out of the "due" bucket). Falls back to a handful of
     * already-mastered items for light review when both are empty (spec §1.8 edge case). */
    private fun assembleSessionItems(userId: UUID, lessonId: UUID): List<AssembledItem> {
        val newItemIds = VocabularyItemsTable
            .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
            .selectAll()
            .where {
                (VocabularyItemsTable.lessonId eq lessonId) and
                    (VocabularyProgressTable.userId eq userId) and
                    VocabularyProgressTable.lastReviewedAt.isNull()
            }
            .limit(NEW_ITEMS_CAP)
            .map { it[VocabularyItemsTable.id] }

        val dueItemIds = VocabularyItemsTable
            .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
            .selectAll()
            .where {
                (VocabularyItemsTable.lessonId neq lessonId) and
                    (VocabularyProgressTable.userId eq userId) and
                    VocabularyProgressTable.lastReviewedAt.isNotNull() and
                    (VocabularyProgressTable.nextReviewAt lessEq Instant.now())
            }
            .limit(DUE_REVIEW_CAP)
            .map { it[VocabularyItemsTable.id] }

        val fallbackItemIds = if (newItemIds.isEmpty() && dueItemIds.isEmpty()) {
            VocabularyItemsTable
                .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
                .selectAll()
                .where { (VocabularyProgressTable.userId eq userId) and (VocabularyProgressTable.masteryState eq "mastered") }
                .limit(MASTERED_FALLBACK_CAP)
                .map { it[VocabularyItemsTable.id] }
        } else {
            emptyList()
        }

        val poolIds = newItemIds + dueItemIds + fallbackItemIds
        if (poolIds.isEmpty()) return emptyList()

        // (id, term, meaning) for every item this user has, for distractor selection - session
        // pools are small (a few dozen items at MVP scale), so one query up front is cheap.
        val allUserVocab = VocabularyItemsTable
            .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
            .selectAll()
            .where { VocabularyProgressTable.userId eq userId }
            .map { Triple(it[VocabularyItemsTable.id], it[VocabularyItemsTable.term], it[VocabularyItemsTable.meaning]) }
        val byId = allUserVocab.associateBy { it.first }

        return poolIds.map { itemId ->
            val (_, term, meaning) = byId.getValue(itemId)
            buildAssembledItem(itemId, term, meaning, allUserVocab)
        }
    }

    private fun buildAssembledItem(
        itemId: UUID,
        term: String,
        meaning: String,
        allUserVocab: List<Triple<UUID, String, String>>,
    ): AssembledItem {
        if (Random.nextBoolean()) return AssembledItem(itemId, "typed_recall", null)

        val termToMeaning = Random.nextBoolean()
        val distractors = allUserVocab.filter { it.first != itemId }
            .shuffled()
            .take(3)
            .map { if (termToMeaning) it.third else it.second }
        val correctAnswer = if (termToMeaning) meaning else term
        val choices = (distractors + correctAnswer).shuffled()

        // Not enough distinct vocabulary anywhere yet to build a real multiple-choice question -
        // fall back to typed recall for this item rather than shipping a 1-or-2-choice question.
        if (choices.size < 3) return AssembledItem(itemId, "typed_recall", null)

        val drillType = if (termToMeaning) "multiple_choice_term_to_meaning" else "multiple_choice_meaning_to_term"
        return AssembledItem(itemId, drillType, choices)
    }
}
