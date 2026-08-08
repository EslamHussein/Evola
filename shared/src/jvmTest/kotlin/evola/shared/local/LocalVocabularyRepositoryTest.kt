package evola.shared.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.ApiResult
import evola.shared.db.EvolaDatabase
import evola.shared.vocabulary.VocabularyCard
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalVocabularyRepositoryTest {

    private fun setup(itemCount: Int = 3): Pair<LocalVocabularyRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        return LocalVocabularyRepository(db) to db
    }

    @Test
    fun `new words are queued via an intro card immediately before their recognition step`() = runTest {
        val (repo, _) = setup(itemCount = 3)
        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertIs<VocabularyCard.Intro>(session.card)
        assertEquals("v0", session.card.itemId)
    }

    @Test
    fun `intro Got it advances straight to the same word's recognition card`() = runTest {
        val (repo, _) = setup(itemCount = 1)
        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        val result = (repo.submitIntro(session.sessionId, session.card.itemId) as ApiResult.Success).data
        val next = result.next!!
        assertIs<VocabularyCard.Recognition>(next.card)
        assertEquals("v0", next.card.itemId)
    }

    @Test
    fun `a full correct ladder walk climbs recognition to wordbank to hint to blind and mastery advances each step`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitIntro(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!

        val recognition = session.card as VocabularyCard.Recognition
        var result = (repo.submitChoice(session.sessionId, recognition.itemId, "word0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)

        val wordbank = result.next!!.card as VocabularyCard.WordBank
        result = (repo.submitChoice(result.next!!.sessionId, wordbank.itemId, "Wort0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("review", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)

        val hint = result.next!!.card as VocabularyCard.Hint
        assertTrue(hint.hintPrefix.isNotEmpty() && "Wort0".startsWith(hint.hintPrefix))
        result = (repo.submitTyped(result.next!!.sessionId, hint.itemId, "Wort0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("mastered", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)

        val blind = result.next!!.card
        assertIs<VocabularyCard.Blind>(blind)
        result = (repo.submitTyped(result.next!!.sessionId, blind.itemId, "Wort0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("mastered", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        // Ladder complete for this word this session - queue is now exhausted.
        assertEquals(null, result.next)
    }

    @Test
    fun `a wrong answer at any rung does not advance the ladder and repeats later, not immediately next`() = runTest {
        val (repo, db) = setup(itemCount = 3)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitIntro(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!

        val wrongWord = session.card.itemId
        assertIs<VocabularyCard.Recognition>(session.card)
        val result = (repo.submitChoice(session.sessionId, wrongWord, "totally wrong") as ApiResult.Success).data
        assertEquals(false, result.correct)
        assertEquals("word0", result.correctAnswer)
        // First-ever drill still exits "introduced" -> "learning", right or wrong - never regresses to unseen.
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, wrongWord).executeAsOne().status)

        // The very next card must NOT be the repeated recognition - other words' cards come first.
        val nextCard = result.next!!.card
        assertTrue(nextCard.itemId != wrongWord, "repeat card must not appear immediately next")

        // Drain forward (answering everything else wrong is fine - we only care whether the wrong
        // word's own recognition rung ever resurfaces, not whether other words progress).
        var current = result.next!!
        var sawRepeatOfWrongWord = false
        var guard = 0
        while (guard < 30 && !sawRepeatOfWrongWord) {
            guard++
            val card = current.card
            if (card.itemId == wrongWord && card is VocabularyCard.Recognition) sawRepeatOfWrongWord = true
            val answerResult = when (card) {
                is VocabularyCard.Intro -> (repo.submitIntro(current.sessionId, card.itemId) as ApiResult.Success).data
                is VocabularyCard.Recognition -> (repo.submitChoice(current.sessionId, card.itemId, "x") as ApiResult.Success).data
                is VocabularyCard.WordBank -> (repo.submitChoice(current.sessionId, card.itemId, "x") as ApiResult.Success).data
                is VocabularyCard.Hint -> (repo.submitTyped(current.sessionId, card.itemId, "x") as ApiResult.Success).data
                is VocabularyCard.Blind -> (repo.submitTyped(current.sessionId, card.itemId, "x") as ApiResult.Success).data
            }
            current = answerResult.next ?: break
        }
        assertTrue(sawRepeatOfWrongWord, "wrong word's repeated recognition card never resurfaced")
    }

    @Test
    fun `complete records session stats and daily activity`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitIntro(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!
        var result = (repo.submitChoice(session.sessionId, session.card.itemId, "word0") as ApiResult.Success).data
        result = (repo.submitChoice(result.next!!.sessionId, result.next!!.card.itemId, "Wort0") as ApiResult.Success).data
        result = (repo.submitTyped(result.next!!.sessionId, result.next!!.card.itemId, "Wort0") as ApiResult.Success).data
        result = (repo.submitTyped(result.next!!.sessionId, result.next!!.card.itemId, "Wort0") as ApiResult.Success).data

        val summary = (repo.complete(session.sessionId, "2026-08-05") as ApiResult.Success).data
        assertEquals(1, summary.wordsLearned)
        assertEquals(100.0, summary.accuracy)
        assertTrue(db.activityQueries.forDate(LOCAL_USER, "2026-08-05").executeAsOneOrNull() != null)
    }

    @Test
    fun `a due-for-review word skips the ladder entirely and opens straight on a blind card`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        // Fast-forward v0 straight to "review" status, due now - simulating a word already seen in
        // an earlier session, rather than walking the ladder to get there.
        db.vocabularyQueries.updateProgress("review", 2L, 0L, 1L, 0L, nowMillis(), LOCAL_USER, "v0")

        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertIs<VocabularyCard.Blind>(session.card)
        assertEquals("v0", session.card.itemId)
    }

    @Test
    fun `updateFlags toggles bookmark`() = runTest {
        val (repo, _) = setup(itemCount = 1)
        val updated = (repo.updateFlags("v0", isBookmarked = true, markedDifficult = null) as ApiResult.Success).data
        assertTrue(updated.isBookmarked)
        assertTrue(!updated.markedDifficult)
    }
}
