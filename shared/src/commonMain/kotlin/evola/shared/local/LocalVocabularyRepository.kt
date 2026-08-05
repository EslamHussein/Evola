package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.db.EvolaDatabase
import evola.shared.db.Vocabulary_pack_words
import evola.shared.srs.MasterySrs
import evola.shared.vocabulary.PackWord
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyPack
import evola.shared.vocabulary.VocabularyPackSummary
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.VocabularyStageAnswerResult
import evola.shared.vocabulary.isTolerantMatch
import kotlin.random.Random

private const val PACK_SIZE = 5
private const val NEW_ITEMS_CAP = 12L
private const val DUE_REVIEW_CAP = 15L
private const val MASTERED_FALLBACK_CAP = 5L
private const val STAGE_COUNT = 7
private const val FREE_PRODUCTION_STAGE_INDEX = 6
private const val SENTENCE_MATCH_THRESHOLD = 0.7
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * On-device Vocabulary Learning (pack/7-stage model) — ports the retired server `VocabularyService`
 * over SQLDelight. One [MasterySrs] call per word, evaluated once all 7 stages are answered. Stage 6
 * (Free Production) grades via an injected AI [grader]; every other gradable stage (2–5) is
 * deterministic. Single-user: user is always [LOCAL_USER].
 */
class LocalVocabularyRepository(
    private val db: EvolaDatabase,
    private val grader: VocabularyFreeProductionGrader,
) : VocabularyRepository {

    override suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularyPack> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Lesson not found"))
        val packId = db.vocabularyQueries.incompletePackForLesson(LOCAL_USER, lessonId).executeAsOneOrNull()?.id
            ?: createPack(lessonId)
            ?: return ApiResult.Failure(DataError.Http(404, "No vocabulary available"))
        return ApiResult.Success(buildCurrentPack(packId))
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
                masteryState = row.p_mastery_state,
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

    override suspend fun answer(packId: String, itemId: String, stageIndex: Int, response: String): ApiResult<VocabularyStageAnswerResult> {
        db.vocabularyQueries.packById(packId, LOCAL_USER).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Pack not found"))
        val position = currentPosition(packId)
            ?: return ApiResult.Failure(DataError.Http(409, "Pack already complete"))
        val (wordRow, currentStage) = position
        if (wordRow.vocabulary_item_id != itemId || currentStage != stageIndex) {
            return ApiResult.Failure(DataError.Http(409, "Out of order"))
        }
        val item = db.vocabularyQueries.itemById(itemId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Item not found"))

        val (correct, feedback) = if (stageIndex == FREE_PRODUCTION_STAGE_INDEX) {
            val result = grader.grade(item.term, response)
            result.passable to result.feedback
        } else {
            gradeDeterministicStage(stageIndex, item.term, item.example_sentence, response) to null
        }

        db.vocabularyQueries.insertStageAnswer(
            newId(), wordRow.id, stageIndex.toLong(), response, correct?.let { if (it) 1L else 0L }, nowMillis(),
        )
        if (stageIndex == STAGE_COUNT - 1) applyMasteryUpdate(wordRow.id, itemId)

        return ApiResult.Success(VocabularyStageAnswerResult(correct, feedback, buildCurrentPack(packId)))
    }

    override suspend fun complete(packId: String, localDate: String): ApiResult<VocabularyPackSummary> {
        val pack = db.vocabularyQueries.packById(packId, LOCAL_USER).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Pack not found"))
        val words = db.vocabularyQueries.wordsByPack(packId).executeAsList()
        val correctCount = words.count { isWordCorrect(it.id) }
        val accuracy = if (words.isNotEmpty()) (correctCount.toDouble() / words.size) * 100.0 else 0.0
        val now = nowMillis()

        db.vocabularyQueries.completePack(now, accuracy, packId)
        db.activityQueries.upsert(newId(), LOCAL_USER, localDate)

        return ApiResult.Success(
            VocabularyPackSummary(
                wordsLearned = words.size,
                accuracy = accuracy,
                timeSeconds = ((now - pack.started_at) / 1000L),
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
                masteryState = row.p_mastery_state,
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

    // --- pack assembly ------------------------------------------------------

    private fun createPack(lessonId: String): String? {
        val poolIds = assemblePackPool(lessonId)
        if (poolIds.isEmpty()) return null

        val packId = newId()
        val nextPackNumber = (db.vocabularyQueries.maxPackNumber(LOCAL_USER, lessonId).executeAsOneOrNull() ?: 0L) + 1L
        db.vocabularyQueries.insertPack(packId, LOCAL_USER, lessonId, nextPackNumber, nowMillis(), poolIds.size.toLong())

        val allVocab = db.vocabularyQueries.allUserVocab(LOCAL_USER).executeAsList()
        poolIds.forEachIndexed { index, itemId ->
            val info = allVocab.first { it.id == itemId }
            db.vocabularyQueries.insertPackWord(
                newId(), packId, itemId, index.toLong(),
                encodeStringList(buildRecognitionChoices(info, allVocab)),
            )
        }
        return packId
    }

    private fun assemblePackPool(lessonId: String): List<String> {
        val newIds = db.vocabularyQueries.newItemsForLesson(lessonId, LOCAL_USER, NEW_ITEMS_CAP).executeAsList()
        val dueIds = db.vocabularyQueries.dueItemsElsewhere(lessonId, LOCAL_USER, nowMillis(), DUE_REVIEW_CAP).executeAsList()
        val fallbackIds = if (newIds.isEmpty() && dueIds.isEmpty()) {
            db.vocabularyQueries.masteredItems(LOCAL_USER, MASTERED_FALLBACK_CAP).executeAsList()
        } else {
            emptyList()
        }
        return (newIds + dueIds + fallbackIds).take(PACK_SIZE)
    }

    private fun buildRecognitionChoices(
        info: evola.shared.db.AllUserVocab,
        allVocab: List<evola.shared.db.AllUserVocab>,
    ): List<String> {
        val correct = info.meaning_ar ?: info.meaning
        val distractors = allVocab.filter { it.id != info.id }.shuffled().take(3).map { it.meaning_ar ?: it.meaning }
        return (distractors + correct).shuffled()
    }

    // --- position / mastery -------------------------------------------------

    private fun currentPosition(packId: String): Pair<Vocabulary_pack_words, Int>? {
        val words = db.vocabularyQueries.wordsByPack(packId).executeAsList()
        for (word in words) {
            val answered = db.vocabularyQueries.answersForWord(word.id).executeAsList().size
            if (answered < STAGE_COUNT) return word to answered
        }
        return null
    }

    private fun isWordCorrect(wordRowId: String): Boolean {
        val gradable = db.vocabularyQueries.answersForWord(wordRowId).executeAsList().filter { it.stage_index >= 2L }
        return gradable.size == 5 && gradable.all { it.correct == 1L }
    }

    private fun applyMasteryUpdate(wordRowId: String, itemId: String) {
        val wordCorrect = isWordCorrect(wordRowId)
        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOneOrNull() ?: return
        val currentState = MasterySrs.State(progress.mastery_state, progress.interval_index.toInt(), progress.correct_streak.toInt())
        val nextState = if (wordCorrect) MasterySrs.onCorrect(currentState) else MasterySrs.onIncorrect(currentState)
        val now = nowMillis()
        val nextReviewAt = now + MasterySrs.intervalDaysFor(nextState.intervalIndex) * MILLIS_PER_DAY
        db.vocabularyQueries.updateProgress(
            nextState.masteryState, nextState.correctStreak.toLong(), nextState.intervalIndex.toLong(),
            nextReviewAt, now, LOCAL_USER, itemId,
        )
    }

    // --- current pack view --------------------------------------------------

    private fun buildCurrentPack(packId: String): VocabularyPack {
        val pack = db.vocabularyQueries.packById(packId, LOCAL_USER).executeAsOne()
        val words = db.vocabularyQueries.wordsByPack(packId).executeAsList()
        val position = currentPosition(packId)
        val (wordRow, stageIndex, ready) = if (position != null) {
            Triple(position.first, position.second, false)
        } else {
            Triple(words.last(), STAGE_COUNT - 1, true)
        }

        val itemId = wordRow.vocabulary_item_id
        val item = db.vocabularyQueries.itemById(itemId).executeAsOne()
        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, itemId).executeAsOne()
        val recognitionChoices = decodeStringList(wordRow.recognition_choices)

        val term = item.term
        val exampleSentence = item.example_sentence
        val sentenceEligible = exampleSentence != null && exampleSentence.contains(term, ignoreCase = true)

        val word = PackWord(
            itemId = itemId,
            term = term,
            meaning = item.meaning,
            gender = item.gender,
            exampleSentence = exampleSentence,
            masteryState = progress.mastery_state,
            meaningAr = item.meaning_ar,
            ipaPronunciation = item.ipa_pronunciation,
            relatedWords = decodeStringList(item.related_words),
            difficultyRating = item.difficulty_rating,
            frequencyRating = item.frequency_rating,
            memoryTip = item.memory_tip,
            isBookmarked = progress.is_bookmarked == 1L,
            markedDifficult = progress.marked_difficult == 1L,
            recognitionChoices = recognitionChoices,
            partialMask = partialMask(term),
            sentenceWithBlank = if (sentenceEligible) blankOutTerm(exampleSentence!!, term) else null,
            sentenceTranslationPrompt = item.example_sentence_translation,
        )

        return VocabularyPack(
            packId = packId,
            packNumber = pack.pack_number.toInt(),
            wordIndex = wordRow.position.toInt(),
            wordsCount = words.size,
            stageIndex = stageIndex,
            word = word,
            readyToComplete = ready,
        )
    }

    // --- grading helpers (ported verbatim from VocabularyService) ------------

    private fun gradeDeterministicStage(stageIndex: Int, term: String, exampleSentence: String?, response: String): Boolean? =
        when (stageIndex) {
            0, 1 -> null
            2, 3 -> isTolerantMatch(term, response)
            4 -> if (exampleSentence != null && exampleSentence.contains(term, ignoreCase = true)) isTolerantMatch(term, response) else true
            5 -> if (exampleSentence != null) isSentenceMatch(exampleSentence, response) else true
            else -> null
        }

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

    private fun blankOutTerm(sentence: String, term: String): String {
        val idx = sentence.indexOf(term, ignoreCase = true)
        if (idx < 0) return sentence
        return sentence.substring(0, idx) + "___" + sentence.substring(idx + term.length)
    }

    private fun partialMask(term: String): String {
        if (term.length <= 2) return term
        val chars = term.toCharArray()
        val maskable = (1 until chars.size - 1).filter { chars[it].isLetter() }
        if (maskable.isEmpty()) return term
        val maskCount = (maskable.size / 2).coerceAtLeast(1)
        maskable.shuffled(Random(term.hashCode())).take(maskCount).forEach { chars[it] = '_' }
        return chars.joinToString(" ")
    }
}
