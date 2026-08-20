package evola.shared.feature.vocabulary.data

import evola.shared.core.network.AnthropicClient
import evola.shared.core.network.AnthropicModels
import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.decodeStringList
import evola.shared.core.common.encodeStringList
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.db.EvolaDatabase
import evola.shared.db.Vocabulary_progress
import evola.shared.feature.vocabulary.domain.VocabularyAnswerResult
import evola.shared.feature.vocabulary.domain.VocabularyCard
import evola.shared.feature.vocabulary.domain.VocabularyItem
import evola.shared.feature.vocabulary.domain.VocabularyRepository
import evola.shared.feature.vocabulary.domain.VocabularySessionState
import evola.shared.feature.vocabulary.domain.VocabularySessionSummary
import evola.shared.feature.vocabulary.domain.VocabularySrs
import evola.shared.feature.vocabulary.domain.WordCategory
import evola.shared.feature.vocabulary.domain.isTolerantMatch
import evola.shared.local.SettingsRepository

private const val DUE_REVIEW_CAP = 20L
private const val PERSONAL_LESSON_TITLE = "Eigene Vokabeln"
private const val MASTERED_FALLBACK_CAP = 5L
private const val POSITION_STEP = 100L
private const val MILLIS_PER_DAY = 86_400_000L
private const val CHOICE_COUNT = 4
private val GRADUATED_STATUSES = setOf("review", "mastered")

/** Reword's per-card undo - see [LocalVocabularyRepository.undoLastGrade]. [previousStatus] null
 * means the item had no progress row yet at grade time (shouldn't normally happen - every item gets
 * a progress row at import - kept nullable defensively rather than assuming). */
private data class UndoSnapshot(
    val queueRowId: String,
    val insertedRepeatRowId: String?,
    val itemId: String,
    val previousStatus: String?,
    val previousCorrectStreak: Long?,
    val previousIncorrectStreak: Long?,
    val previousIntervalIndex: Long?,
    val previousNextReviewAt: Long?,
    val previousLastSeenAt: Long?,
    val correctDelta: Long,
    val incorrectDelta: Long,
)

/**
 * On-device Vocabulary Learning - a Reword-style swipe queue rather than a forced ladder. A word is
 * always a [VocabularyCard.New] the first time it's shown: swipe left ("I already know this word")
 * fast-tracks it straight into the review schedule ([submitAlreadyKnown], bypassing [VocabularySrs]
 * entirely - this is the one deliberate exception to routing every state change through the pure
 * ladder functions); swipe right ("Start learning this word") calls [VocabularySrs.introduce] and
 * re-queues it as a [VocabularyCard.Practice] card later in the same session ([submitStartLearning]).
 * A [VocabularyCard.Practice] card - covering both "still learning" and "due for review" - can be
 * graded a plain self-reported swipe ([submitSelfGrade]), a non-graded "not ready yet" swipe
 * ([submitKeepShowing], which touches nothing but the queue), a typed check ([submitTyped]), or a
 * multiple-choice check ([submitChoice]). Every graded path funnels into [gradePracticeAndAdvance],
 * which drives [VocabularySrs.onCorrect]/[onIncorrect] and re-queues the word (via [requeueLater])
 * whenever it isn't done for this session yet - wrong answers always come back, and right answers
 * come back only until the word actually graduates to `review`/`mastered`. Single-user: user is
 * always [LOCAL_USER]. No AI grading call anywhere in this flow - every graded step is decided
 * deterministically (exact match for the multiple-choice check, [isTolerantMatch] for typed).
 */
class LocalVocabularyRepository(
    private val db: EvolaDatabase,
    private val anthropic: AnthropicClient,
    private val settingsRepository: SettingsRepository,
) : VocabularyRepository {

    /** In-memory only, per [undoLastGrade]'s own doc comment - not part of the durable session
     * state, cleared on process death same as any other in-memory cache. */
    private val lastUndoBySession = mutableMapOf<String, UndoSnapshot>()

    override suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularySessionState> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        val sessionId = db.vocabularyQueries.incompleteSessionForLesson(LOCAL_USER, lessonId).executeAsOneOrNull()?.id
            ?: createSession(lessonId)
            ?: return fail(404, "No vocabulary available", "lessonId=$lessonId")
        return buildSessionState(sessionId)?.let { ApiResult.Success(it) }
            ?: fail(409, "Session already complete", "sessionId=$sessionId")
    }

    /** Same red/yellow/green split as [evola.shared.feature.onboarding.data.LocalGoalsRepository.vocabularyBreakdown]
     * (struggling = incorrect_streak > 0; mastered = "mastered" status, which - per
     * [VocabularySrs.onIncorrect] - implies incorrect_streak is always 0; learning = touched at
     * least once but neither struggling nor mastered - "unseen" words are excluded since the user
     * hasn't actually started them), so a word picked here always lands in the same bucket Home
     * showed it in. */
    override suspend fun startCategorySession(goalId: String, category: WordCategory, limit: Int): ApiResult<VocabularySessionState> {
        val rows = db.vocabularyQueries.wordStatusesByGoal(LOCAL_USER, goalId).executeAsList()
        val picked = rows.filter { row ->
            when (category) {
                WordCategory.STRUGGLING -> row.incorrect_streak > 0
                WordCategory.MASTERED -> row.status == VocabularySrs.STATUSES.last() && row.incorrect_streak == 0L
                WordCategory.LEARNING -> row.incorrect_streak == 0L &&
                    row.status != VocabularySrs.STATUSES.last() &&
                    row.status != VocabularySrs.STATUSES.first()
            }
        }.map { it.item_id }.take(limit)
        if (picked.isEmpty()) return fail(404, "No words in this category", "goalId=$goalId category=$category")

        val sessionId = newId()
        db.vocabularyQueries.insertSession(sessionId, LOCAL_USER, null, 1L, nowMillis(), 0L, picked.size.toLong())
        var position = 0L
        picked.forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "practice", "due_review", buildChoicesFor(itemId))
            position += POSITION_STEP
        }
        return buildSessionState(sessionId)?.let { ApiResult.Success(it) }
            ?: fail(409, "Session already complete", "sessionId=$sessionId")
    }

    override suspend fun startModeSession(goalId: String, mode: evola.shared.feature.vocabulary.domain.SessionMode): ApiResult<VocabularySessionState> {
        val now = nowMillis()
        val dailyGoal = settingsRepository.current().dailyNewWordGoal.toLong()
        val newIds = if (mode == evola.shared.feature.vocabulary.domain.SessionMode.REVIEW_ONLY) {
            emptyList()
        } else {
            db.vocabularyQueries.newItemsForGoal(LOCAL_USER, goalId, dailyGoal).executeAsList()
        }
        val dueIds = if (mode == evola.shared.feature.vocabulary.domain.SessionMode.NEW_ONLY) {
            emptyList()
        } else {
            db.vocabularyQueries.dueItemsForGoal(LOCAL_USER, goalId, now, DUE_REVIEW_CAP).executeAsList()
        }
        if (newIds.isEmpty() && dueIds.isEmpty()) {
            return fail(404, "Nothing available for this mode", "goalId=$goalId mode=$mode")
        }

        val sessionId = newId()
        db.vocabularyQueries.insertSession(sessionId, LOCAL_USER, null, 1L, now, newIds.size.toLong(), dueIds.size.toLong())
        var position = 0L
        dueIds.forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "practice", "due_review", buildChoicesFor(itemId))
            position += POSITION_STEP
        }
        newIds.forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "new", "new", null)
            position += POSITION_STEP
        }
        return buildSessionState(sessionId)?.let { ApiResult.Success(it) }
            ?: fail(409, "Session already complete", "sessionId=$sessionId")
    }

    override suspend fun listVocabulary(lessonId: String): ApiResult<List<VocabularyItem>> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        val items = db.vocabularyQueries.itemsWithProgressByLesson(lessonId, LOCAL_USER).executeAsList().map { row ->
            VocabularyItem(
                itemId = row.id,
                term = row.term,
                meaning = row.meaning,
                gender = row.gender,
                exampleSentence = row.example_sentence,
                exampleSentenceTranslation = row.example_sentence_translation,
                partOfSpeech = row.part_of_speech,
                plural = row.plural,
                status = row.p_status,
                nativeMeaning = row.native_meaning,
                ipaPronunciation = row.ipa_pronunciation,
                relatedWords = decodeStringList(row.related_words),
                difficultyRating = row.difficulty_rating,
                frequencyRating = row.frequency_rating,
                memoryTip = row.memory_tip,
                grammarNote = row.grammar_note,
                isBookmarked = row.p_is_bookmarked == 1L,
                markedDifficult = row.p_marked_difficult == 1L,
            )
        }
        return ApiResult.Success(items)
    }

    override suspend fun submitAlreadyKnown(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "new") ?: return sessionOrOrderError(sessionId, itemId, "new")
        val now = nowMillis()
        db.vocabularyQueries.answerQueueItem(now, null, null, queueRow.id)

        // Bypasses VocabularySrs on purpose - "I already know this" is a fast-track straight into
        // the review schedule, not a graded practice attempt, so it never touches the pure ladder
        // functions the way every other transition in this file does.
        val nextReviewAt = now + VocabularySrs.intervalDaysFor(0) * MILLIS_PER_DAY
        db.vocabularyQueries.updateProgress("review", 1L, 0L, 0L, nextReviewAt, now, LOCAL_USER, itemId)

        // No requeue - the word is done for this session; it'll resurface as a due review later.
        return ApiResult.Success(VocabularyAnswerResult(correct = null, next = buildSessionState(sessionId)))
    }

    override suspend fun submitStartLearning(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "new") ?: return sessionOrOrderError(sessionId, itemId, "new")
        val now = nowMillis()
        db.vocabularyQueries.answerQueueItem(now, null, null, queueRow.id)

        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        val next = VocabularySrs.introduce(progress.toState())
        db.vocabularyQueries.updateProgress(
            next.status, next.correctStreak.toLong(), next.incorrectStreak.toLong(), next.intervalIndex.toLong(),
            progress.next_review_at, now, LOCAL_USER, itemId,
        )

        requeueLater(sessionId, itemId, "new")
        return ApiResult.Success(VocabularyAnswerResult(correct = null, next = buildSessionState(sessionId)))
    }

    override suspend fun submitSelfGrade(sessionId: String, itemId: String, correct: Boolean): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "practice") ?: return sessionOrOrderError(sessionId, itemId, "practice")
        val (next, justMastered) = gradePracticeAndAdvance(sessionId, itemId, queueRow.id, correct, response = null)
        return ApiResult.Success(VocabularyAnswerResult(correct = correct, next = next, justMastered = justMastered))
    }

    override suspend fun submitKeepShowing(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "practice") ?: return sessionOrOrderError(sessionId, itemId, "practice")
        // Deliberately no VocabularySrs call and no session-counter increment - "keep showing" isn't
        // a miss, it's "not ready to grade yet", so the word's SRS state must be untouched.
        db.vocabularyQueries.answerQueueItem(nowMillis(), null, null, queueRow.id)
        requeueLater(sessionId, itemId, "repeat")
        return ApiResult.Success(VocabularyAnswerResult(correct = null, next = buildSessionState(sessionId)))
    }

    override suspend fun submitChoice(sessionId: String, itemId: String, selectedChoice: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "practice") ?: return sessionOrOrderError(sessionId, itemId, "practice")
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        val expected = dictionaryForm(item.term, item.gender)
        val correct = selectedChoice == expected
        val (next, justMastered) = gradePracticeAndAdvance(sessionId, itemId, queueRow.id, correct, response = selectedChoice)

        return ApiResult.Success(
            VocabularyAnswerResult(
                correct = correct,
                correctAnswer = expected,
                completedSentence = item.example_sentence,
                next = next,
                justMastered = justMastered,
            ),
        )
    }

    override suspend fun submitTyped(sessionId: String, itemId: String, response: String): ApiResult<VocabularyAnswerResult> {
        val queueRow = expectQueueRow(sessionId, itemId, "practice") ?: return sessionOrOrderError(sessionId, itemId, "practice")
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        // Typed recall asks for the word's dictionary form from meaning alone (no sentence to infer
        // grammatical gender from), so a noun's answer must include its article - "der Hund", not
        // just "Hund" - the same way the list/session screens already display it.
        val expected = dictionaryForm(item.term, item.gender)
        val correct = isTolerantMatch(expected, response)
        val (next, justMastered) = gradePracticeAndAdvance(sessionId, itemId, queueRow.id, correct, response = response)

        return ApiResult.Success(
            VocabularyAnswerResult(
                correct = correct,
                correctAnswer = expected,
                completedSentence = item.example_sentence,
                next = next,
                justMastered = justMastered,
            ),
        )
    }

    override suspend fun complete(sessionId: String, localDate: String): ApiResult<VocabularySessionSummary> {
        val session = db.vocabularyQueries.sessionById(sessionId, LOCAL_USER).executeAsOneOrNull()
            ?: return fail(404, "Session not found", "sessionId=$sessionId")
        val wordsLearned = db.vocabularyQueries.wordsPracticedInSession(sessionId).executeAsOne().toInt()
        val totalAnswered = session.correct_count + session.incorrect_count
        val accuracy = if (totalAnswered > 0) (session.correct_count.toDouble() / totalAnswered) * 100.0 else 0.0
        val now = nowMillis()

        db.vocabularyQueries.completeSession(now, localDate, sessionId)
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
            ?: return fail(404, "Item not found", "itemId=$itemId")
        isBookmarked?.let { db.vocabularyQueries.setBookmarked(if (it) 1L else 0L, LOCAL_USER, itemId) }
        markedDifficult?.let { db.vocabularyQueries.setMarkedDifficult(if (it) 1L else 0L, LOCAL_USER, itemId) }
        return ApiResult.Success(loadItemWithProgress(itemId))
    }

    override suspend fun updateItem(itemId: String, term: String, meaning: String, nativeMeaning: String?): ApiResult<VocabularyItem> {
        db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        db.vocabularyQueries.updateItemContent(term, meaning, nativeMeaning, itemId)
        return ApiResult.Success(loadItemWithProgress(itemId))
    }

    override suspend fun deleteItem(itemId: String): ApiResult<Unit> {
        db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        db.vocabularyQueries.deleteItem(itemId)
        return ApiResult.Success(Unit)
    }

    override suspend fun resetLessonProgress(lessonId: String): ApiResult<Unit> {
        db.vocabularyQueries.resetLessonProgress(LOCAL_USER, lessonId)
        return ApiResult.Success(Unit)
    }

    override suspend fun resetAllProgress(): ApiResult<Unit> {
        db.vocabularyQueries.resetAllProgress(LOCAL_USER)
        return ApiResult.Success(Unit)
    }

    override suspend fun createStarterLesson(goalId: String, lessonTitle: String, words: List<evola.shared.feature.vocabulary.domain.StarterWord>): ApiResult<Unit> {
        db.goalsQueries.selectById(goalId).executeAsOneOrNull()
            ?: return fail(404, "Goal not found", "goalId=$goalId")

        val now = nowMillis()
        val materialId = newId()
        db.materialsQueries.insert(
            materialId, LOCAL_USER, goalId, lessonTitle, "starter-${newId()}", "READY",
            "text/plain", 0L, null, "entire", null, null, null, now,
        )
        val newLessonId = newId()
        val nextNumber = (db.lessonsQueries.selectByGoal(goalId).executeAsList().maxOfOrNull { it.number } ?: 0L) + 1L
        db.lessonsQueries.insert(newLessonId, materialId, goalId, nextNumber, lessonTitle, "ready", null, now)
        db.transaction {
            words.forEach { (term, meaning, nativeMeaning) -> insertBareWord(newLessonId, term, meaning, nativeMeaning) }
        }
        return ApiResult.Success(Unit)
    }

    override suspend fun explainItem(itemId: String): ApiResult<String> {
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        item.ai_note?.let { return ApiResult.Success(it) }

        val system = "You explain a single vocabulary word to a language learner in 2-3 short, " +
            "plain-language sentences: what it means, and one concrete usage or memory tip beyond " +
            "what's already obvious from its translation. No headers, no markdown, no repeating the " +
            "translation verbatim."
        val user = buildString {
            append("Word: ${item.term}")
            append(" (${item.meaning})")
            item.example_sentence?.let { append("\nExample: $it") }
        }
        return when (val result = anthropic.complete(AnthropicModels.SMALL, 300, system, user)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val note = result.data.trim()
                db.vocabularyQueries.updateAiNote(note, itemId)
                ApiResult.Success(note)
            }
        }
    }

    override suspend fun markAlreadyKnown(itemId: String): ApiResult<VocabularyItem> {
        db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        val now = nowMillis()
        // Same fast-track as submitAlreadyKnown (see its own comment) - deliberately bypasses
        // VocabularySrs, just reachable outside a session this time.
        val nextReviewAt = now + VocabularySrs.intervalDaysFor(0) * MILLIS_PER_DAY
        db.vocabularyQueries.updateProgress("review", 1L, 0L, 0L, nextReviewAt, now, LOCAL_USER, itemId)
        return ApiResult.Success(loadItemWithProgress(itemId))
    }

    override suspend fun copyToPersonalList(goalId: String, itemId: String): ApiResult<VocabularyItem> {
        val source = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return fail(404, "Item not found", "itemId=$itemId")
        val personalLessonId = getOrCreatePersonalLesson(goalId)
        val newItemId = newId()
        db.vocabularyQueries.insertItem(
            newItemId, personalLessonId, source.term, source.meaning, source.gender, source.example_sentence,
            source.part_of_speech, source.plural, source.grammatical_case, source.example_sentence_translation,
            source.native_meaning, source.ipa_pronunciation, source.related_words, source.difficulty_rating,
            source.frequency_rating, source.memory_tip, source.grammar_note, nowMillis(),
        )
        db.vocabularyQueries.insertProgress(newId(), LOCAL_USER, newItemId, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        return ApiResult.Success(loadItemWithProgress(newItemId))
    }

    /** Reword's "Eigene Vokabeln" - a single lesson (auto-created on first use, found afterwards by
     * its fixed title) holding every word copied from elsewhere in the goal. Needs its own synthetic
     * `materials` row too (lessons.material_id is NOT NULL) - a minimal READY placeholder, never
     * shown as a real material anywhere since nothing lists materials by content. */
    private fun getOrCreatePersonalLesson(goalId: String): String {
        val existing = db.lessonsQueries.selectByGoal(goalId).executeAsList().firstOrNull { it.title == PERSONAL_LESSON_TITLE }
        if (existing != null) return existing.id

        val now = nowMillis()
        val materialId = newId()
        db.materialsQueries.insert(
            materialId, LOCAL_USER, goalId, PERSONAL_LESSON_TITLE, "personal-$goalId", "READY",
            "text/plain", 0L, null, "entire", null, null, null, now,
        )
        val lessonId = newId()
        val nextNumber = (db.lessonsQueries.selectByGoal(goalId).executeAsList().maxOfOrNull { it.number } ?: 0L) + 1L
        db.lessonsQueries.insert(lessonId, materialId, goalId, nextNumber, PERSONAL_LESSON_TITLE, "ready", null, now)
        return lessonId
    }

    override suspend fun addCustomWord(lessonId: String, term: String, meaning: String, nativeMeaning: String?): ApiResult<VocabularyItem> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        val itemId = insertBareWord(lessonId, term, meaning, nativeMeaning)
            ?: return fail(422, "Term and meaning are required", "lessonId=$lessonId")
        return ApiResult.Success(loadItemWithProgress(itemId))
    }

    override suspend fun importWords(lessonId: String, rows: List<Triple<String, String, String?>>): ApiResult<Int> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        var inserted = 0
        db.transaction {
            rows.forEach { (term, meaning, nativeMeaning) ->
                if (insertBareWord(lessonId, term, meaning, nativeMeaning) != null) inserted++
            }
        }
        return ApiResult.Success(inserted)
    }

    /** Shared by [addCustomWord] (one row, reveals the failure reason) and [importWords] (many
     * rows, silently skips a blank one rather than failing the whole batch) - null means the row
     * was blank and nothing was inserted. */
    private fun insertBareWord(lessonId: String, term: String, meaning: String, nativeMeaning: String?): String? {
        val trimmedTerm = term.trim()
        val trimmedMeaning = meaning.trim()
        if (trimmedTerm.isEmpty() || trimmedMeaning.isEmpty()) return null
        val itemId = newId()
        db.vocabularyQueries.insertItem(
            itemId, lessonId, trimmedTerm, trimmedMeaning, null, null,
            null, null, null, null, nativeMeaning?.trim()?.ifBlank { null }, null, null, null, null, null, null, nowMillis(),
        )
        db.vocabularyQueries.insertProgress(newId(), LOCAL_USER, itemId, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        return itemId
    }

    private fun loadItemWithProgress(itemId: String): VocabularyItem {
        val row = db.vocabularyQueries.itemWithProgress(itemId, LOCAL_USER).executeAsOne()
        return VocabularyItem(
            itemId = row.id,
            term = row.term,
            meaning = row.meaning,
            gender = row.gender,
            exampleSentence = row.example_sentence,
            exampleSentenceTranslation = row.example_sentence_translation,
            partOfSpeech = row.part_of_speech,
            plural = row.plural,
            status = row.p_status,
            nativeMeaning = row.native_meaning,
            ipaPronunciation = row.ipa_pronunciation,
            relatedWords = decodeStringList(row.related_words),
            difficultyRating = row.difficulty_rating,
            frequencyRating = row.frequency_rating,
            memoryTip = row.memory_tip,
            grammarNote = row.grammar_note,
            isBookmarked = row.p_is_bookmarked == 1L,
            markedDifficult = row.p_marked_difficult == 1L,
        )
    }

    // --- shared grading/advancement ------------------------------------------

    /** Every graded [VocabularyCard.Practice] answer - self-graded swipe, typed, or multiple-choice
     * - drives the word's cross-session SRS status/schedule the same way. On correct: if the word
     * hasn't graduated to `review`/`mastered` yet, it comes back later this session (mirrors
     * Reword's "keeps showing until you graduate it" loop); once graduated, it's done for this
     * session. On incorrect: always comes back later this session, same as before. */
    /** Second element is true exactly when this answer moves the word's status into "mastered" for
     * the first time - see [VocabularyAnswerResult.justMastered]. */
    private fun gradePracticeAndAdvance(
        sessionId: String,
        itemId: String,
        queueRowId: String,
        correct: Boolean,
        response: String?,
    ): Pair<VocabularySessionState?, Boolean> {
        val now = nowMillis()
        db.vocabularyQueries.answerQueueItem(now, if (correct) 1L else 0L, response, queueRowId)

        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull()
        var graduated = false
        var justMastered = false
        var insertedRepeatRowId: String? = null
        if (progress != null) {
            val state = progress.toState()
            val next = if (correct) VocabularySrs.onCorrect(state) else VocabularySrs.onIncorrect(state)
            val nextReviewAt = now + VocabularySrs.intervalDaysFor(next.intervalIndex) * MILLIS_PER_DAY
            db.vocabularyQueries.updateProgress(
                next.status, next.correctStreak.toLong(), next.incorrectStreak.toLong(), next.intervalIndex.toLong(),
                nextReviewAt, now, LOCAL_USER, itemId,
            )
            graduated = next.status in GRADUATED_STATUSES
            justMastered = progress.status != "mastered" && next.status == "mastered"
        }

        db.vocabularyQueries.incrementSessionCounters(if (correct) 1L else 0L, if (correct) 0L else 1L, sessionId)

        if (!correct || !graduated) insertedRepeatRowId = requeueLater(sessionId, itemId, "repeat")

        // Reword's per-card undo - see [undoLastGrade]. Captured last (after every mutation above
        // succeeds) so a snapshot only ever exists for a grade that actually completed.
        lastUndoBySession[sessionId] = UndoSnapshot(
            queueRowId = queueRowId,
            insertedRepeatRowId = insertedRepeatRowId,
            itemId = itemId,
            previousStatus = progress?.status,
            previousCorrectStreak = progress?.correct_streak,
            previousIncorrectStreak = progress?.incorrect_streak,
            previousIntervalIndex = progress?.interval_index,
            previousNextReviewAt = progress?.next_review_at,
            previousLastSeenAt = progress?.last_seen_at,
            correctDelta = if (correct) 1L else 0L,
            incorrectDelta = if (correct) 0L else 1L,
        )

        return buildSessionState(sessionId) to justMastered
    }

    /** Re-queues [itemId] as a `practice` row past every existing row in the session's queue - a
     * plain append at `maxQueuePosition + 100`, so it reappears later without ever needing to shift
     * anything already queued. Used for "start learning", "keep showing", and every re-attempt after
     * a graded answer that isn't done for the session yet. Returns the inserted row's id, so a
     * grading call can undo the insert later (see [undoLastGrade]). */
    private fun requeueLater(sessionId: String, itemId: String, origin: String): String {
        val target = (db.vocabularyQueries.maxQueuePosition(sessionId).executeAsOne().MAX ?: 0L) + POSITION_STEP
        val id = newId()
        db.vocabularyQueries.insertQueueItem(id, sessionId, target, itemId, "practice", origin, buildChoicesFor(itemId))
        return id
    }

    override suspend fun undoLastGrade(sessionId: String): ApiResult<VocabularySessionState?> {
        val snapshot = lastUndoBySession.remove(sessionId) ?: return ApiResult.Success(null)

        snapshot.insertedRepeatRowId?.let { db.vocabularyQueries.deleteQueueItem(it) }
        db.vocabularyQueries.unanswerQueueItem(snapshot.queueRowId)
        if (snapshot.previousStatus != null) {
            db.vocabularyQueries.updateProgress(
                snapshot.previousStatus, snapshot.previousCorrectStreak ?: 0L, snapshot.previousIncorrectStreak ?: 0L,
                snapshot.previousIntervalIndex ?: 0L, snapshot.previousNextReviewAt ?: 0L, snapshot.previousLastSeenAt,
                LOCAL_USER, snapshot.itemId,
            )
        }
        db.vocabularyQueries.incrementSessionCounters(-snapshot.correctDelta, -snapshot.incorrectDelta, sessionId)

        return ApiResult.Success(buildSessionState(sessionId))
    }

    private fun buildChoicesFor(itemId: String): String {
        val item = db.vocabularyQueries.itemById(itemId).executeAsOne()
        val correct = dictionaryForm(item.term, item.gender)
        val pool = db.vocabularyQueries.allUserVocab(LOCAL_USER).executeAsList().filter { it.id != itemId }
        val distractors = pool.map { it.term }.shuffled().take(CHOICE_COUNT - 1)
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
        val dailyGoal = settingsRepository.current().dailyNewWordGoal.toLong()
        val newIds = db.vocabularyQueries.newItemsForLesson(LOCAL_USER, lessonId, dailyGoal).executeAsList()
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
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "practice", "due_review", buildChoicesFor(itemId))
            position += POSITION_STEP
        }
        newIds.forEach { itemId ->
            db.vocabularyQueries.insertQueueItem(newId(), sessionId, position, itemId, "new", "new", null)
            position += POSITION_STEP
        }
        return sessionId
    }

    private fun currentQueueRow(sessionId: String) = db.vocabularyQueries.nextQueueItem(sessionId).executeAsOneOrNull()

    private fun expectQueueRow(sessionId: String, itemId: String, expectedCardType: String) =
        currentQueueRow(sessionId)?.takeIf { it.vocabulary_item_id == itemId && it.card_type == expectedCardType }

    private fun sessionOrOrderError(sessionId: String, itemId: String, expected: String): ApiResult.Failure {
        val queueRow = currentQueueRow(sessionId)
            ?: return fail(409, "Session already complete", "sessionId=$sessionId")
        return fail(409, "Out of order", "sessionId=$sessionId itemId=$itemId expected=$expected actual=${queueRow.card_type}")
    }

    // --- current card view -----------------------------------------------

    private fun buildSessionState(sessionId: String): VocabularySessionState? {
        val session = db.vocabularyQueries.sessionById(sessionId, LOCAL_USER).executeAsOne()
        val queueRow = currentQueueRow(sessionId) ?: return null
        val queue = db.vocabularyQueries.queueForSession(sessionId).executeAsList()
        val completed = queue.count { it.answered_at != null }
        // A word can span several queue rows (requeued after "keep showing" or a wrong answer);
        // count only distinct words reached so far, not rows, so "word 3 of 5" advances once per
        // word, not per re-attempt.
        val wordIndex = queue.filter { it.position <= queueRow.position }
            .map { it.vocabulary_item_id }.distinct().size
        val totalWords = (session.new_words_count + session.review_words_count).toInt()

        val item = db.vocabularyQueries.itemById(queueRow.vocabulary_item_id).executeAsOne()
        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, queueRow.vocabulary_item_id).executeAsOne()
        val isBookmarked = progress.is_bookmarked == 1L
        val isDifficult = progress.marked_difficult == 1L

        val card: VocabularyCard = if (queueRow.card_type == "new") {
            VocabularyCard.New(
                itemId = item.id,
                term = item.term,
                gender = item.gender,
                partOfSpeech = item.part_of_speech,
                plural = item.plural,
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
                aiExplanation = item.ai_note,
            )
        } else {
            VocabularyCard.Practice(
                itemId = item.id,
                meaning = item.native_meaning ?: item.meaning,
                grammarNote = item.grammar_note,
                exampleSentence = item.example_sentence,
                exampleSentenceTranslation = item.example_sentence_translation,
                isBookmarked = isBookmarked,
                markedDifficult = isDifficult,
                choices = decodeStringList(queueRow.choices),
            )
        }

        return VocabularySessionState(
            sessionId = sessionId,
            sessionNumber = session.session_number.toInt(),
            cardsCompleted = completed,
            cardsRemaining = queue.size - completed,
            card = card,
            origin = queueRow.origin,
            wordIndex = wordIndex,
            totalWords = totalWords,
        )
    }

    /** A noun's article is part of what a learner must memorize alongside it (there's no way to
     * derive "der/die/das" from the word itself), so the dictionary form used for typed-recall
     * grading, multiple-choice options, and hints is "der Hund", not the bare "Hund". No-op for
     * words without a gender (verbs, adjectives, ...). */
    private fun dictionaryForm(term: String, gender: String?): String =
        if (gender.isNullOrBlank()) term else "$gender $term"

    private fun Vocabulary_progress.toState() =
        VocabularySrs.State(status, interval_index.toInt(), correct_streak.toInt(), incorrect_streak.toInt())

    private fun fail(code: Int, message: String, context: String): ApiResult.Failure {
        EvolaLog.d("vocabulary", "$message ($context)")
        return ApiResult.Failure(DataError.Http(code, message))
    }
}
