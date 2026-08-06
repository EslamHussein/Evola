package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.db.EvolaDatabase
import evola.shared.db.Vocabulary_progress
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularySessionState
import evola.shared.vocabulary.VocabularySessionSummary
import evola.shared.vocabulary.VocabularySrs
import evola.shared.vocabulary.isTolerantMatch
import kotlin.math.ceil

private const val NEW_WORDS_TARGET = 8L
private const val DUE_REVIEW_CAP = 20L
private const val MASTERED_FALLBACK_CAP = 5L
private const val POSITION_STEP = 100L
private const val MILLIS_PER_DAY = 86_400_000L
private const val CHOICE_COUNT = 4

/** Ladder rungs for a *new* word, in order. `intro` is the fixed ungraded prerequisite (not part of
 * this list); a due-for-review word skips the whole ladder and gets a single "blind" card instead. */
private val LADDER = listOf("recognition", "wordbank", "hint", "blind")

/**
 * On-device Vocabulary Learning — Lingvist-style flat SRS queue, extended with a per-word difficulty
 * ladder for new words: intro -> recognition (MC) -> wordbank (tap) -> hint (typed, half pre-filled)
 * -> blind (typed, no help). A word only ever has ONE queued card at a time; each correct answer
 * splices the next harder rung immediately after (`answeredPosition + 1`); each wrong answer appends
 * one more card of the SAME rung past every existing row (`maxQueuePosition + POSITION_STEP`) so it
 * resurfaces later without disrupting anything already queued. Due-for-review words (and the
 * mastered-fallback pool) skip the ladder entirely - one "blind" card, same as before. Single-user:
 * user is always [LOCAL_USER]. No AI grading call anywhere in this flow - every graded step is
 * decided deterministically (exact match for Recognition/WordBank, [isTolerantMatch] for Hint/Blind).
 */
class LocalVocabularyRepository(private val db: EvolaDatabase) : VocabularyRepository {

    override suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularySessionState> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Lesson not found"))
        val sessionId = db.vocabularyQueries.incompleteSessionForLesson(LOCAL_USER, lessonId).executeAsOneOrNull()?.id
            ?: createSession(lessonId)
            ?: return ApiResult.Failure(DataError.Http(404, "No vocabulary available"))
        return buildSessionState(sessionId)?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(DataError.Http(409, "Session already complete"))
    }

    override suspend fun listVocabulary(lessonId: String): ApiResult<List<VocabularyItem>> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Lesson not found"))
        val items = db.vocabularyQueries.itemsWithProgressByLesson(lessonId, LOCAL_USER).executeAsList().map { row ->
            VocabularyItem(
                itemId = row.id,
                term = row.term,
                meaning = row.meaning,
                gender = row.gender,
                exampleSentence = row.example_sentence,
                status = row.p_status,
                meaningAr = row.meaning_ar,
                ipaPronunciation = row.ipa_pronunciation,
                relatedWords = decodeStringList(row.related_words),
                difficultyRating = row.difficulty_rating,
                frequencyRating = row.frequency_rating,
                memoryTip = row.memory_tip,
                isBookmarked = row.p_is_bookmarked == 1L,
                markedDifficult = row.p_marked_difficult == 1L,
            )
        }
        return ApiResult.Success(items)
    }

    override suspend fun submitIntro(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = currentQueueRow(sessionId)
            ?: return ApiResult.Failure(DataError.Http(409, "Session already complete"))
        if (queueRow.vocabulary_item_id != itemId || queueRow.card_type != "intro") {
            return ApiResult.Failure(DataError.Http(409, "Out of order"))
        }
        val now = nowMillis()
        db.vocabularyQueries.answerQueueItem(now, null, null, queueRow.id)

        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Item not found"))
        val next = VocabularySrs.introduce(progress.toState())
        db.vocabularyQueries.updateProgress(
            next.status, next.correctStreak.toLong(), next.incorrectStreak.toLong(), next.intervalIndex.toLong(),
            progress.next_review_at, now, LOCAL_USER, itemId,
        )

        // Recognition is already pre-inserted by createSession (intro + recognition are the only
        // two rows queued upfront for a new word) - no splice needed here.
        return ApiResult.Success(VocabularyAnswerResult(correct = null, next = buildSessionState(sessionId)))
    }

    override suspend fun submitChoice(sessionId: String, itemId: String, selectedChoice: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = currentQueueRow(sessionId)
            ?: return ApiResult.Failure(DataError.Http(409, "Session already complete"))
        if (queueRow.vocabulary_item_id != itemId || queueRow.card_type !in setOf("recognition", "wordbank")) {
            return ApiResult.Failure(DataError.Http(409, "Out of order"))
        }
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Item not found"))
        val correctAnswer = if (queueRow.card_type == "recognition") item.meaning_ar ?: item.meaning else item.term
        val correct = selectedChoice == correctAnswer

        return gradeAndAdvance(sessionId, itemId, queueRow.id, queueRow.card_type, queueRow.position, correct, selectedChoice).let {
            ApiResult.Success(
                VocabularyAnswerResult(correct = correct, correctAnswer = correctAnswer, completedSentence = null, next = it),
            )
        }
    }

    override suspend fun submitTyped(sessionId: String, itemId: String, response: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = currentQueueRow(sessionId)
            ?: return ApiResult.Failure(DataError.Http(409, "Session already complete"))
        if (queueRow.vocabulary_item_id != itemId || queueRow.card_type !in setOf("hint", "blind")) {
            return ApiResult.Failure(DataError.Http(409, "Out of order"))
        }
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Item not found"))
        val correct = isTolerantMatch(item.term, response)

        return gradeAndAdvance(sessionId, itemId, queueRow.id, queueRow.card_type, queueRow.position, correct, response).let {
            ApiResult.Success(
                VocabularyAnswerResult(correct = correct, correctAnswer = item.term, completedSentence = item.example_sentence, next = it),
            )
        }
    }

    override suspend fun complete(sessionId: String, localDate: String): ApiResult<VocabularySessionSummary> {
        val session = db.vocabularyQueries.sessionById(sessionId, LOCAL_USER).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Session not found"))
        val wordsLearned = db.vocabularyQueries.wordsPracticedInSession(sessionId).executeAsOne().toInt()
        val totalAnswered = session.correct_count + session.incorrect_count
        val accuracy = if (totalAnswered > 0) (session.correct_count.toDouble() / totalAnswered) * 100.0 else 0.0
        val now = nowMillis()

        db.vocabularyQueries.completeSession(now, sessionId)
        db.activityQueries.upsert(newId(), LOCAL_USER, localDate)

        return ApiResult.Success(
            VocabularySessionSummary(
                sessionNumber = session.session_number.toInt(),
                wordsLearned = wordsLearned,
                accuracy = accuracy,
                timeSeconds = (now - session.started_at) / 1000L,
                newWordsCount = session.new_words_count.toInt(),
                reviewWordsCount = session.review_words_count.toInt(),
            ),
        )
    }

    override suspend fun updateFlags(itemId: String, isBookmarked: Boolean?, markedDifficult: Boolean?): ApiResult<VocabularyItem> {
        db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Item not found"))
        isBookmarked?.let { db.vocabularyQueries.setBookmarked(if (it) 1L else 0L, LOCAL_USER, itemId) }
        markedDifficult?.let { db.vocabularyQueries.setMarkedDifficult(if (it) 1L else 0L, LOCAL_USER, itemId) }

        val row = db.vocabularyQueries.itemWithProgress(itemId, LOCAL_USER).executeAsOne()
        return ApiResult.Success(
            VocabularyItem(
                itemId = row.id,
                term = row.term,
                meaning = row.meaning,
                gender = row.gender,
                exampleSentence = row.example_sentence,
                status = row.p_status,
                meaningAr = row.meaning_ar,
                ipaPronunciation = row.ipa_pronunciation,
                relatedWords = decodeStringList(row.related_words),
                difficultyRating = row.difficulty_rating,
                frequencyRating = row.frequency_rating,
                memoryTip = row.memory_tip,
                isBookmarked = row.p_is_bookmarked == 1L,
                markedDifficult = row.p_marked_difficult == 1L,
            ),
        )
    }

    // --- shared grading/advancement ------------------------------------------

    /** Every graded answer, at any ladder rung, drives the word's cross-session SRS status/schedule
     * exactly like a single Fill-Blank answer always did - the ladder is a scaffolding wrapper over
     * the same per-answer SRS event model, not a second "only the last step counts" scheme. On
     * correct: splice the next rung immediately after (or nothing, if this was "blind" - the ladder
     * is complete for this word this session). On incorrect: append a repeat of the SAME rung past
     * every existing row. */
    private fun gradeAndAdvance(
        sessionId: String,
        itemId: String,
        queueRowId: String,
        cardType: String,
        position: Long,
        correct: Boolean,
        response: String,
    ): VocabularySessionState? {
        val now = nowMillis()
        db.vocabularyQueries.answerQueueItem(now, if (correct) 1L else 0L, response, queueRowId)

        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
        if (progress != null) {
            val state = progress.toState()
            val next = if (correct) VocabularySrs.onCorrect(state) else VocabularySrs.onIncorrect(state)
            val nextReviewAt = now + VocabularySrs.intervalDaysFor(next.intervalIndex) * MILLIS_PER_DAY
            db.vocabularyQueries.updateProgress(
                next.status, next.correctStreak.toLong(), next.incorrectStreak.toLong(), next.intervalIndex.toLong(),
                nextReviewAt, now, LOCAL_USER, itemId,
            )
        }

        db.vocabularyQueries.incrementSessionCounters(if (correct) 1L else 0L, if (correct) 0L else 1L, sessionId)

        if (correct) {
            nextLadderStep(cardType)?.let { spliceNextStep(sessionId, itemId, it, position) }
        } else {
            appendRepeat(sessionId, itemId, cardType)
        }
        return buildSessionState(sessionId)
    }

    private fun nextLadderStep(current: String): String? {
        val idx = LADDER.indexOf(current)
        return if (idx < 0 || idx == LADDER.lastIndex) null else LADDER[idx + 1]
    }

    /** Advance a word to its next rung, shown as the very next card - a plain no-shift insert at
     * `afterPosition + 1`, safely inside the [POSITION_STEP]-wide gap left around every row. */
    private fun spliceNextStep(sessionId: String, itemId: String, cardType: String, afterPosition: Long) {
        val choices = if (cardType in setOf("recognition", "wordbank")) buildChoicesFor(itemId, cardType) else null
        db.vocabularyQueries.insertQueueItem(newId(), sessionId, afterPosition + 1, itemId, cardType, "new", choices)
    }

    /** Re-queue the SAME rung after a wrong answer, appended past every existing row - simply "later
     * in the session," never immediately next, and never needs shifting anything. */
    private fun appendRepeat(sessionId: String, itemId: String, cardType: String) {
        val target = (db.vocabularyQueries.maxQueuePosition(sessionId).executeAsOne().MAX ?: 0L) + POSITION_STEP
        val choices = if (cardType in setOf("recognition", "wordbank")) buildChoicesFor(itemId, cardType) else null
        db.vocabularyQueries.insertQueueItem(newId(), sessionId, target, itemId, cardType, "repeat", choices)
    }

    private fun buildChoicesFor(itemId: String, cardType: String): String {
        val item = db.vocabularyQueries.itemById(itemId).executeAsOne()
        val correct = if (cardType == "recognition") item.meaning_ar ?: item.meaning else item.term
        val pool = db.vocabularyQueries.allUserVocab(LOCAL_USER).executeAsList().filter { it.id != itemId }
        val distractors = if (cardType == "recognition") {
            pool.map { it.meaning_ar ?: it.meaning }
        } else {
            pool.map { it.term }
        }.shuffled().take(CHOICE_COUNT - 1)
        return encodeStringList((distractors + correct).shuffled())
    }

    // --- session queue assembly ----------------------------------------------

    private fun createSession(lessonId: String): String? {
        val now = nowMillis()
        val dueInLesson = db.vocabularyQueries.dueItemsInLesson(lessonId, LOCAL_USER, now, DUE_REVIEW_CAP).executeAsList()
        val dueElsewhere = if (dueInLesson.size < DUE_REVIEW_CAP) {
            db.vocabularyQueries.dueItemsElsewhere(lessonId, LOCAL_USER, now, DUE_REVIEW_CAP - dueInLesson.size).executeAsList()
        } else {
            emptyList()
        }
        val dueIds = dueInLesson + dueElsewhere
        val newIds = db.vocabularyQueries.newItemsForLesson(LOCAL_USER, lessonId, NEW_WORDS_TARGET).executeAsList()
        val fallbackIds = if (dueIds.isEmpty() && newIds.isEmpty()) {
            db.vocabularyQueries.masteredItems(LOCAL_USER, MASTERED_FALLBACK_CAP).executeAsList()
        } else {
            emptyList()
        }
        if (dueIds.isEmpty() && newIds.isEmpty() && fallbackIds.isEmpty()) return null

        val sessionId = newId()
        val nextSessionNumber = (db.vocabularyQueries.maxSessionNumber(LOCAL_USER, lessonId).executeAsOneOrNull() ?: 0L) + 1L
        db.vocabularyQueries.insertSession(
            sessionId, LOCAL_USER, lessonId, nextSessionNumber, now,
            newIds.size.toLong(), (dueIds.size + fallbackIds.size).toLong(),
        )

        var position = 0L
        (dueIds + fallbackIds).forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "blind", "due_review", null)
            position += POSITION_STEP
        }
        newIds.forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "intro", "new", null)
            position += POSITION_STEP
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "recognition", "new", buildChoicesFor(itemId, "recognition"))
            position += POSITION_STEP
        }
        return sessionId
    }

    private fun currentQueueRow(sessionId: String) = db.vocabularyQueries.nextQueueItem(sessionId).executeAsOneOrNull()

    // --- current card view -----------------------------------------------

    private fun buildSessionState(sessionId: String): VocabularySessionState? {
        val session = db.vocabularyQueries.sessionById(sessionId, LOCAL_USER).executeAsOne()
        val queueRow = currentQueueRow(sessionId) ?: return null
        val queue = db.vocabularyQueries.queueForSession(sessionId).executeAsList()
        val completed = queue.count { it.answered_at != null }

        val item = db.vocabularyQueries.itemById(queueRow.vocabulary_item_id).executeAsOne()
        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, queueRow.vocabulary_item_id).executeAsOne()
        val isBookmarked = progress.is_bookmarked == 1L
        val isDifficult = progress.marked_difficult == 1L
        val exampleSentence = item.example_sentence
        val blankEligible = exampleSentence != null && exampleSentence.contains(item.term, ignoreCase = true)
        val sentenceWithBlank = if (blankEligible) blankOutTerm(exampleSentence!!, item.term) else item.term

        val card: VocabularyCard = when (queueRow.card_type) {
            "intro" -> VocabularyCard.Intro(
                itemId = item.id,
                term = item.term,
                gender = item.gender,
                ipaPronunciation = item.ipa_pronunciation,
                meaning = item.meaning,
                exampleSentence = item.example_sentence,
                exampleSentenceTranslation = item.example_sentence_translation,
                grammarNote = item.grammar_note,
                relatedWords = decodeStringList(item.related_words),
                difficultyRating = item.difficulty_rating,
                frequencyRating = item.frequency_rating,
                memoryTip = item.memory_tip,
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
            )
            "recognition" -> VocabularyCard.Recognition(
                itemId = item.id,
                term = item.term,
                choices = decodeStringList(queueRow.choices),
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
            )
            "wordbank" -> VocabularyCard.WordBank(
                itemId = item.id,
                sentenceWithBlank = sentenceWithBlank,
                sentenceTranslation = item.example_sentence_translation,
                grammarNote = item.grammar_note,
                choices = decodeStringList(queueRow.choices),
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
            )
            "hint" -> VocabularyCard.Hint(
                itemId = item.id,
                sentenceWithBlank = sentenceWithBlank,
                sentenceTranslation = item.example_sentence_translation,
                grammarNote = item.grammar_note,
                hintPrefix = item.term.take(ceil(item.term.length / 2.0).toInt()),
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
            )
            else -> VocabularyCard.Blind(
                itemId = item.id,
                sentenceWithBlank = sentenceWithBlank,
                sentenceTranslation = item.example_sentence_translation,
                grammarNote = item.grammar_note,
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
            )
        }

        return VocabularySessionState(
            sessionId = sessionId,
            sessionNumber = session.session_number.toInt(),
            cardsCompleted = completed,
            cardsRemaining = queue.size - completed,
            card = card,
            origin = queueRow.origin,
        )
    }

    private fun blankOutTerm(sentence: String, term: String): String {
        val idx = sentence.indexOf(term, ignoreCase = true)
        if (idx < 0) return sentence
        return sentence.substring(0, idx) + "___" + sentence.substring(idx + term.length)
    }

    private fun Vocabulary_progress.toState() =
        VocabularySrs.State(status, interval_index.toInt(), correct_streak.toInt(), incorrect_streak.toInt())
}
