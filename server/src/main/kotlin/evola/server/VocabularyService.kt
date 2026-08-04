package evola.server

import evola.shared.vocabulary.isTolerantMatch
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

private const val PACK_SIZE = 5
private const val NEW_ITEMS_CAP = 12
private const val DUE_REVIEW_CAP = 15
private const val MASTERED_FALLBACK_CAP = 5
private const val STAGE_COUNT = 7
private const val FREE_PRODUCTION_STAGE_INDEX = 6
private const val SENTENCE_MATCH_THRESHOLD = 0.7
private val GRADABLE_STAGE_INDICES = 2..6

@Serializable
data class VocabularyItemResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("meaning_ar") val meaningAr: String? = null,
    @SerialName("ipa_pronunciation") val ipaPronunciation: String? = null,
    @SerialName("related_words") val relatedWords: List<String> = emptyList(),
    @SerialName("difficulty_rating") val difficultyRating: String? = null,
    @SerialName("frequency_rating") val frequencyRating: String? = null,
    @SerialName("memory_tip") val memoryTip: String? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("marked_difficult") val markedDifficult: Boolean = false,
)

/** One word's full Discover-card payload plus whichever stage-specific fields the current stage
 * needs - simpler for the client to always receive the full word and pick what it needs per stage
 * than to invent a distinct DTO per one of the 7 stages. */
@Serializable
data class PackWordResponse(
    @SerialName("item_id") val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    @SerialName("example_sentence") val exampleSentence: String? = null,
    @SerialName("mastery_state") val masteryState: String,
    @SerialName("meaning_ar") val meaningAr: String? = null,
    @SerialName("ipa_pronunciation") val ipaPronunciation: String? = null,
    @SerialName("related_words") val relatedWords: List<String> = emptyList(),
    @SerialName("difficulty_rating") val difficultyRating: String? = null,
    @SerialName("frequency_rating") val frequencyRating: String? = null,
    @SerialName("memory_tip") val memoryTip: String? = null,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("marked_difficult") val markedDifficult: Boolean = false,
    @SerialName("recognition_choices") val recognitionChoices: List<String> = emptyList(),
    @SerialName("partial_mask") val partialMask: String? = null,
    @SerialName("sentence_with_blank") val sentenceWithBlank: String? = null,
    @SerialName("sentence_translation_prompt") val sentenceTranslationPrompt: String? = null,
)

@Serializable
data class VocabularyPackResponse(
    @SerialName("pack_id") val packId: String,
    @SerialName("pack_number") val packNumber: Int,
    @SerialName("word_index") val wordIndex: Int,
    @SerialName("words_count") val wordsCount: Int,
    @SerialName("stage_index") val stageIndex: Int,
    val word: PackWordResponse,
    /** True once every word in the pack has finished all 7 stages - the client should show the
     * "Finish pack" action and call [VocabularyService.complete] rather than render another stage. */
    @SerialName("ready_to_complete") val readyToComplete: Boolean = false,
)

@Serializable
data class VocabularyAnswerRequest(
    @SerialName("item_id") val itemId: String,
    @SerialName("stage_index") val stageIndex: Int,
    val response: String,
)

@Serializable
data class VocabularyAnswerResponse(
    val correct: Boolean? = null,
    val feedback: String? = null,
    val next: VocabularyPackResponse? = null,
)

@Serializable
data class VocabularyPackCompleteResponse(
    @SerialName("words_learned") val wordsLearned: Int,
    val accuracy: Double,
    @SerialName("time_seconds") val timeSeconds: Long,
)

@Serializable
data class VocabularyFlagsRequest(
    @SerialName("is_bookmarked") val isBookmarked: Boolean? = null,
    @SerialName("marked_difficult") val markedDifficult: Boolean? = null,
)

private data class VocabInfo(
    val id: UUID,
    val term: String,
    val meaning: String,
    val meaningAr: String?,
    val exampleSentence: String?,
    val exampleSentenceTranslation: String?,
)

/**
 * Vocabulary Learning, pack/stage architecture (design handoff Phase 7): a lesson's vocabulary is
 * worked through in packs of ~[PACK_SIZE] words, each word walked through 7 fixed stages
 * (Discover, Recognition, Reverse Recall, Partial Recall, Sentence Completion, Translation, Free
 * Production). Replaces the old flat drill-queue model; `vocabulary_sessions`/
 * `vocabulary_session_items` are left in place, unused, per the plan's deprecate-not-drop decision.
 *
 * Mastery-update granularity: exactly one [MasterySrs] call per word, evaluated once all 7 stages
 * are answered (see [applyMasteryUpdate]) - not per-stage, since running the SRS ladder 7x/word
 * would advance/reset it nonsensically fast. A word counts "correct" iff every gradable stage
 * (indices 2-6) was answered correctly; stages 0 (Discover, passive) and 1 (Recognition - the
 * design always reveals the correct answer regardless of tap) never factor in.
 */
class VocabularyService(
    private val database: Database,
    private val grader: VocabularyGrader,
) {

    suspend fun startOrResumeSession(userId: String, lessonId: String): VocabularyPackResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val lessonUuid = UUID.fromString(lessonId)
            val lessonRow = LessonsTable.selectAll().where { LessonsTable.id eq lessonUuid }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (!ownsLesson(userUuid, lessonRow[LessonsTable.goalId])) return@newSuspendedTransaction null

            val existingPackId = VocabularyPacksTable.selectAll()
                .where {
                    (VocabularyPacksTable.userId eq userUuid) and
                        (VocabularyPacksTable.lessonId eq lessonUuid) and
                        VocabularyPacksTable.completedAt.isNull()
                }
                .singleOrNull()?.get(VocabularyPacksTable.id)

            val packId = existingPackId ?: createPack(userUuid, lessonUuid) ?: return@newSuspendedTransaction null
            buildCurrentPackResponse(userUuid, packId)
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
                .map { it.toVocabularyItemResponse() }
        }

    /** AI grading (Stage 6, Free Production) happens outside any DB transaction - a suspend
     * network call shouldn't hold a JDBC connection open, same pattern as the batch extraction
     * workers' [callModel]-outside-transaction shape. */
    suspend fun answer(userId: String, packId: String, request: VocabularyAnswerRequest): VocabularyAnswerResponse? {
        val userUuid = UUID.fromString(userId)
        val packUuid = runCatching { UUID.fromString(packId) }.getOrNull() ?: return null

        val pending = newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyPacksTable.selectAll()
                .where { (VocabularyPacksTable.id eq packUuid) and (VocabularyPacksTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val position = currentPosition(packUuid) ?: return@newSuspendedTransaction null
            val (wordRow, stageIndex) = position
            val vocabularyItemId = wordRow[VocabularyPackWordsTable.vocabularyItemId]
            if (vocabularyItemId.toString() != request.itemId || stageIndex != request.stageIndex) {
                return@newSuspendedTransaction null
            }

            val itemRow = VocabularyItemsTable.selectAll().where { VocabularyItemsTable.id eq vocabularyItemId }.singleOrNull()
                ?: return@newSuspendedTransaction null

            PendingAnswer(
                wordRowId = wordRow[VocabularyPackWordsTable.id],
                vocabularyItemId = vocabularyItemId,
                stageIndex = stageIndex,
                term = itemRow[VocabularyItemsTable.term],
                exampleSentence = itemRow[VocabularyItemsTable.exampleSentence],
            )
        } ?: return null

        val (correct, feedback) = if (pending.stageIndex == FREE_PRODUCTION_STAGE_INDEX) {
            val result = grader.grade(pending.term, request.response)
            result.passable to result.feedback
        } else {
            gradeDeterministicStage(pending.stageIndex, pending.term, pending.exampleSentence, request.response) to null
        }

        return newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyStageAnswersTable.insert {
                it[id] = UUID.randomUUID()
                it[packWordId] = pending.wordRowId
                it[this.stageIndex] = pending.stageIndex.toShort()
                it[userResponse] = request.response
                it[this.correct] = correct
                it[answeredAt] = Instant.now()
            }

            if (pending.stageIndex == STAGE_COUNT - 1) {
                applyMasteryUpdate(userUuid, pending.wordRowId, pending.vocabularyItemId)
            }

            VocabularyAnswerResponse(correct, feedback, buildCurrentPackResponse(userUuid, packUuid))
        }
    }

    suspend fun complete(userId: String, packId: String): VocabularyPackCompleteResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val packUuid = runCatching { UUID.fromString(packId) }.getOrNull() ?: return@newSuspendedTransaction null
            val packRow = VocabularyPacksTable.selectAll()
                .where { (VocabularyPacksTable.id eq packUuid) and (VocabularyPacksTable.userId eq userUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val words = VocabularyPackWordsTable.selectAll().where { VocabularyPackWordsTable.packId eq packUuid }.toList()
            val correctCount = words.count { wordRow -> isWordCorrect(wordRow[VocabularyPackWordsTable.id]) }
            val accuracy = if (words.isNotEmpty()) (correctCount.toDouble() / words.size) * 100.0 else 0.0
            val startedAt = packRow[VocabularyPacksTable.startedAt]
            val now = Instant.now()

            VocabularyPacksTable.update({ VocabularyPacksTable.id eq packUuid }) {
                it[completedAt] = now
                it[this.accuracy] = BigDecimal(accuracy).setScale(2, RoundingMode.HALF_UP)
            }

            VocabularyPackCompleteResponse(
                wordsLearned = words.size,
                accuracy = accuracy,
                timeSeconds = ChronoUnit.SECONDS.between(startedAt, now),
            )
        }

    suspend fun updateFlags(userId: String, itemId: String, request: VocabularyFlagsRequest): VocabularyItemResponse? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val userUuid = UUID.fromString(userId)
            val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull() ?: return@newSuspendedTransaction null

            // Ownership: a vocabulary_progress row for (user, item) only exists if this user
            // actually has this vocabulary item - no separate lesson/goal join needed.
            VocabularyProgressTable.selectAll()
                .where { (VocabularyProgressTable.userId eq userUuid) and (VocabularyProgressTable.vocabularyItemId eq itemUuid) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            VocabularyProgressTable.update({
                (VocabularyProgressTable.userId eq userUuid) and (VocabularyProgressTable.vocabularyItemId eq itemUuid)
            }) { statement ->
                request.isBookmarked?.let { statement[isBookmarked] = it }
                request.markedDifficult?.let { statement[markedDifficult] = it }
            }

            VocabularyItemsTable
                .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
                .selectAll()
                .where { (VocabularyItemsTable.id eq itemUuid) and (VocabularyProgressTable.userId eq userUuid) }
                .single()
                .toVocabularyItemResponse()
        }

    private fun ownsLesson(userId: UUID, goalId: UUID): Boolean =
        GoalsTable.selectAll().where { (GoalsTable.id eq goalId) and (GoalsTable.userId eq userId) }.any()

    private fun ResultRow.toVocabularyItemResponse() = VocabularyItemResponse(
        itemId = this[VocabularyItemsTable.id].toString(),
        term = this[VocabularyItemsTable.term],
        meaning = this[VocabularyItemsTable.meaning],
        gender = this[VocabularyItemsTable.gender],
        exampleSentence = this[VocabularyItemsTable.exampleSentence],
        masteryState = this[VocabularyProgressTable.masteryState],
        meaningAr = this[VocabularyItemsTable.meaningAr],
        ipaPronunciation = this[VocabularyItemsTable.ipaPronunciation],
        relatedWords = decodeRelatedWords(this[VocabularyItemsTable.relatedWords]),
        difficultyRating = this[VocabularyItemsTable.difficultyRating],
        frequencyRating = this[VocabularyItemsTable.frequencyRating],
        memoryTip = this[VocabularyItemsTable.memoryTip],
        isBookmarked = this[VocabularyProgressTable.isBookmarked],
        markedDifficult = this[VocabularyProgressTable.markedDifficult],
    )

    private fun decodeRelatedWords(json: String?): List<String> =
        json?.let { runCatching { MATERIALS_JSON.decodeFromString(ListSerializer(String.serializer()), it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    /** Creates a new pack from a fresh pool draw (new-from-this-lesson + due-review-from-elsewhere
     * + mastered-fallback, same caps/order as the old flat-session model), capped at [PACK_SIZE].
     * Once a pack completes, [applyMasteryUpdate] sets `last_reviewed_at` on its words, which
     * naturally removes them from the "new" bucket the next time this runs - no separate
     * already-used-word exclusion needed. Returns null only if literally no vocabulary exists yet
     * anywhere for this user (extraction not done, or a lesson with 0 items and no other progress). */
    private fun createPack(userId: UUID, lessonId: UUID): UUID? {
        val poolIds = assemblePackPool(userId, lessonId)
        if (poolIds.isEmpty()) return null

        val packId = UUID.randomUUID()
        val nextPackNumber = (
            VocabularyPacksTable.selectAll()
                .where { (VocabularyPacksTable.userId eq userId) and (VocabularyPacksTable.lessonId eq lessonId) }
                .maxOfOrNull { it[VocabularyPacksTable.packNumber] } ?: 0
            ) + 1

        VocabularyPacksTable.insert {
            it[id] = packId
            it[this.userId] = userId
            it[this.lessonId] = lessonId
            it[packNumber] = nextPackNumber
            it[startedAt] = Instant.now()
            it[itemsCount] = poolIds.size
            it[accuracy] = null
        }

        val allUserVocab = loadAllUserVocab(userId)
        val byId = allUserVocab.associateBy { it.id }
        poolIds.forEachIndexed { index, itemId ->
            val info = byId.getValue(itemId)
            VocabularyPackWordsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.packId] = packId
                it[vocabularyItemId] = itemId
                it[position] = index
                it[recognitionChoices] = MATERIALS_JSON.encodeToString(
                    ListSerializer(String.serializer()),
                    buildRecognitionChoices(info, allUserVocab),
                )
            }
        }

        return packId
    }

    private fun assemblePackPool(userId: UUID, lessonId: UUID): List<UUID> {
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

        return (newItemIds + dueItemIds + fallbackItemIds).take(PACK_SIZE)
    }

    private fun loadAllUserVocab(userId: UUID): List<VocabInfo> =
        VocabularyItemsTable
            .join(VocabularyProgressTable, JoinType.INNER, VocabularyItemsTable.id, VocabularyProgressTable.vocabularyItemId)
            .selectAll()
            .where { VocabularyProgressTable.userId eq userId }
            .map {
                VocabInfo(
                    id = it[VocabularyItemsTable.id],
                    term = it[VocabularyItemsTable.term],
                    meaning = it[VocabularyItemsTable.meaning],
                    meaningAr = it[VocabularyItemsTable.meaningAr],
                    exampleSentence = it[VocabularyItemsTable.exampleSentence],
                    exampleSentenceTranslation = it[VocabularyItemsTable.exampleSentenceTranslation],
                )
            }

    /** Arabic meaning when extracted (V12+), falling back to English - same "honest partial state"
     * convention as the Vocabulary List screen. */
    private fun buildRecognitionChoices(info: VocabInfo, allUserVocab: List<VocabInfo>): List<String> {
        val correctAnswer = info.meaningAr ?: info.meaning
        val distractors = allUserVocab.filter { it.id != info.id }
            .shuffled()
            .take(3)
            .map { it.meaningAr ?: it.meaning }
        return (distractors + correctAnswer).shuffled()
    }

    /** First pack_word (in position order) that hasn't answered all [STAGE_COUNT] stages, and the
     * next stage index it needs - fully derived from `vocabulary_stage_answers`, no stored pointer
     * column, same "derive from data" convention the old flat-session model used. Null means every
     * word in the pack has finished all stages (pack is ready for [complete]). */
    private fun currentPosition(packId: UUID): Pair<ResultRow, Int>? {
        val words = VocabularyPackWordsTable.selectAll()
            .where { VocabularyPackWordsTable.packId eq packId }
            .orderBy(VocabularyPackWordsTable.position, SortOrder.ASC)
            .toList()

        for (wordRow in words) {
            val answeredStages = VocabularyStageAnswersTable.selectAll()
                .where { VocabularyStageAnswersTable.packWordId eq wordRow[VocabularyPackWordsTable.id] }
                .count()
            if (answeredStages < STAGE_COUNT) return wordRow to answeredStages.toInt()
        }
        return null
    }

    private fun isWordCorrect(wordRowId: UUID): Boolean {
        val gradableAnswers = VocabularyStageAnswersTable.selectAll()
            .where { (VocabularyStageAnswersTable.packWordId eq wordRowId) and (VocabularyStageAnswersTable.stageIndex greaterEq 2.toShort()) }
            .toList()
        return gradableAnswers.size == GRADABLE_STAGE_INDICES.count() && gradableAnswers.all { it[VocabularyStageAnswersTable.correct] == true }
    }

    private fun applyMasteryUpdate(userId: UUID, wordRowId: UUID, vocabularyItemId: UUID) {
        val wordCorrect = isWordCorrect(wordRowId)

        val progressRow = VocabularyProgressTable.selectAll()
            .where { (VocabularyProgressTable.userId eq userId) and (VocabularyProgressTable.vocabularyItemId eq vocabularyItemId) }
            .singleOrNull() ?: return

        val currentState = MasterySrs.State(
            masteryState = progressRow[VocabularyProgressTable.masteryState],
            intervalIndex = progressRow[VocabularyProgressTable.intervalIndex].toInt(),
            correctStreak = progressRow[VocabularyProgressTable.correctStreak],
        )
        val nextState = if (wordCorrect) MasterySrs.onCorrect(currentState) else MasterySrs.onIncorrect(currentState)
        val now = Instant.now()
        val nextReviewAt = now.plus(MasterySrs.intervalDaysFor(nextState.intervalIndex), ChronoUnit.DAYS)

        VocabularyProgressTable.update({
            (VocabularyProgressTable.userId eq userId) and (VocabularyProgressTable.vocabularyItemId eq vocabularyItemId)
        }) {
            it[masteryState] = nextState.masteryState
            it[intervalIndex] = nextState.intervalIndex.toShort()
            it[correctStreak] = nextState.correctStreak
            it[this.nextReviewAt] = nextReviewAt
            it[lastReviewedAt] = now
        }
    }

    private fun buildCurrentPackResponse(userId: UUID, packId: UUID): VocabularyPackResponse {
        val packRow = VocabularyPacksTable.selectAll().where { VocabularyPacksTable.id eq packId }.single()
        val words = VocabularyPackWordsTable.selectAll()
            .where { VocabularyPackWordsTable.packId eq packId }
            .orderBy(VocabularyPackWordsTable.position, SortOrder.ASC)
            .toList()

        val position = currentPosition(packId)
        val (wordRow, stageIndex, readyToComplete) = if (position != null) {
            Triple(position.first, position.second, false)
        } else {
            Triple(words.last(), STAGE_COUNT - 1, true)
        }

        val vocabularyItemId = wordRow[VocabularyPackWordsTable.vocabularyItemId]
        val itemRow = VocabularyItemsTable.selectAll().where { VocabularyItemsTable.id eq vocabularyItemId }.single()
        val progressRow = VocabularyProgressTable.selectAll()
            .where { (VocabularyProgressTable.userId eq userId) and (VocabularyProgressTable.vocabularyItemId eq vocabularyItemId) }
            .single()

        val recognitionChoices = wordRow[VocabularyPackWordsTable.recognitionChoices]
            ?.let { runCatching { MATERIALS_JSON.decodeFromString(ListSerializer(String.serializer()), it) }.getOrDefault(emptyList()) }
            ?: emptyList()

        val term = itemRow[VocabularyItemsTable.term]
        val exampleSentence = itemRow[VocabularyItemsTable.exampleSentence]
        val sentenceEligible = exampleSentence != null && exampleSentence.contains(term, ignoreCase = true)

        val word = PackWordResponse(
            itemId = vocabularyItemId.toString(),
            term = term,
            meaning = itemRow[VocabularyItemsTable.meaning],
            gender = itemRow[VocabularyItemsTable.gender],
            exampleSentence = exampleSentence,
            masteryState = progressRow[VocabularyProgressTable.masteryState],
            meaningAr = itemRow[VocabularyItemsTable.meaningAr],
            ipaPronunciation = itemRow[VocabularyItemsTable.ipaPronunciation],
            relatedWords = decodeRelatedWords(itemRow[VocabularyItemsTable.relatedWords]),
            difficultyRating = itemRow[VocabularyItemsTable.difficultyRating],
            frequencyRating = itemRow[VocabularyItemsTable.frequencyRating],
            memoryTip = itemRow[VocabularyItemsTable.memoryTip],
            isBookmarked = progressRow[VocabularyProgressTable.isBookmarked],
            markedDifficult = progressRow[VocabularyProgressTable.markedDifficult],
            recognitionChoices = recognitionChoices,
            partialMask = partialMask(term),
            sentenceWithBlank = if (sentenceEligible) blankOutTerm(exampleSentence!!, term) else null,
            sentenceTranslationPrompt = itemRow[VocabularyItemsTable.exampleSentenceTranslation],
        )

        return VocabularyPackResponse(
            packId = packId.toString(),
            packNumber = packRow[VocabularyPacksTable.packNumber],
            wordIndex = wordRow[VocabularyPackWordsTable.position],
            wordsCount = words.size,
            stageIndex = stageIndex,
            word = word,
            readyToComplete = readyToComplete,
        )
    }

    /** Stage 0 (Discover) and 1 (Recognition) are never graded - the design always reveals the
     * correct answer regardless of input. Stages 2/3 match the typed answer against the full term;
     * stage 4 does too (the "blanked term" is just the stimulus built via [blankOutTerm], not a
     * different expected answer) but auto-passes when the source data has no valid sentence to
     * blank, rather than blocking the pack on a pre-V10 extraction gap. Stage 5 fuzzy-matches a
     * full sentence, not a single word - see [isSentenceMatch]. */
    private fun gradeDeterministicStage(stageIndex: Int, term: String, exampleSentence: String?, response: String): Boolean? =
        when (stageIndex) {
            0, 1 -> null
            2, 3 -> isTolerantMatch(term, response)
            4 -> if (exampleSentence != null && exampleSentence.contains(term, ignoreCase = true)) {
                isTolerantMatch(term, response)
            } else {
                true
            }
            5 -> if (exampleSentence != null) isSentenceMatch(exampleSentence, response) else true
            else -> null
        }

    /** Deterministic fuzzy match for Stage 5 (Translation): normalize whitespace/case/punctuation,
     * then require a high token-overlap ratio - cheap and adequate for a fixed, known target
     * sentence, avoiding a second AI-call surface beyond Stage 6's genuinely open-ended grading. */
    private fun isSentenceMatch(expected: String, actual: String): Boolean {
        val expectedTokens = normalizeToTokens(expected)
        val actualTokens = normalizeToTokens(actual)
        if (expectedTokens.isEmpty()) return false
        if (expectedTokens == actualTokens) return true
        val overlap = expectedTokens.intersect(actualTokens).size
        return overlap.toDouble() / expectedTokens.size >= SENTENCE_MATCH_THRESHOLD
    }

    private fun normalizeToTokens(text: String): Set<String> =
        text.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .split(Regex("\\s+"))
            .filterTo(mutableSetOf()) { it.isNotBlank() }

    /** Replaces the term's first case-insensitive occurrence in the sentence with a blank. */
    private fun blankOutTerm(sentence: String, term: String): String {
        val idx = sentence.indexOf(term, ignoreCase = true)
        if (idx < 0) return sentence
        return sentence.substring(0, idx) + "___" + sentence.substring(idx + term.length)
    }

    /** Stage 3 (Partial Recall)'s masked German word (e.g. "T e r m _ _") - deterministic per term
     * (seeded on its own hash) so repeated fetches of the same word's stage-3 view stay stable. */
    private fun partialMask(term: String): String {
        if (term.length <= 2) return term
        val chars = term.toCharArray()
        val maskable = (1 until chars.size - 1).filter { chars[it].isLetter() }
        if (maskable.isEmpty()) return term
        val maskCount = (maskable.size / 2).coerceAtLeast(1)
        maskable.shuffled(Random(term.hashCode())).take(maskCount).forEach { chars[it] = '_' }
        return chars.joinToString(" ")
    }

    private data class PendingAnswer(
        val wordRowId: UUID,
        val vocabularyItemId: UUID,
        val stageIndex: Int,
        val term: String,
        val exampleSentence: String?,
    )
}
