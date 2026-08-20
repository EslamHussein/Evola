package evola.shared.feature.vocabulary.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.nowMillis
import evola.shared.db.EvolaDatabase
import evola.shared.feature.vocabulary.domain.VocabularyCard
import evola.shared.feature.vocabulary.domain.WordCategory
import evola.shared.local.LocalSettingsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
                null, null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        val anthropic = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val settingsRepository = LocalSettingsRepository(db)
        return LocalVocabularyRepository(db, anthropic, settingsRepository) to db
    }

    @Test
    fun `new words are queued as a New card`() = runTest {
        val (repo, _) = setup(itemCount = 3)
        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertIs<VocabularyCard.New>(session.card)
        assertEquals("v0", session.card.itemId)
    }

    @Test
    fun `already known fast-tracks straight into the review schedule with no practice card`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        val result = (repo.submitAlreadyKnown(session.sessionId, session.card.itemId) as ApiResult.Success).data
        assertNull(result.correct)

        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne()
        assertEquals("review", progress.status)
        assertEquals(1L, progress.correct_streak)
        assertEquals(0L, progress.incorrect_streak)
        // The only word in the lesson is now scheduled for a future review, not shown again this session.
        assertNull(result.next)
    }

    @Test
    fun `start learning re-queues the word as a practice card`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        val result = (repo.submitStartLearning(session.sessionId, session.card.itemId) as ApiResult.Success).data
        assertNull(result.correct)

        assertEquals("introduced", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        val next = result.next!!
        assertIs<VocabularyCard.Practice>(next.card)
        assertEquals("v0", next.card.itemId)
    }

    @Test
    fun `a requeued practice card's choices are a full 4-option set when other words exist`() = runTest {
        // itemCount=4 so there's a large enough distractor pool for a full 4-option choices list. The
        // requeue always lands past every existing row, so the other three words' New cards come
        // first - drain through them to reach v0's own Practice card.
        val (repo, _) = setup(itemCount = 4)
        var current = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        var practiceForV0: VocabularyCard.Practice? = null
        var guard = 0
        while (guard < 20 && practiceForV0 == null) {
            guard++
            val card = current.card
            if (card.itemId == "v0" && card is VocabularyCard.Practice) {
                practiceForV0 = card
                break
            }
            val result = when (card) {
                is VocabularyCard.New -> (repo.submitStartLearning(current.sessionId, card.itemId) as ApiResult.Success).data
                is VocabularyCard.Practice -> (repo.submitKeepShowing(current.sessionId, card.itemId) as ApiResult.Success).data
            }
            current = result.next ?: break
        }
        assertEquals(4, practiceForV0?.choices?.size)
    }

    @Test
    fun `self-graded correct answers requeue until the word graduates to review`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitStartLearning(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!

        // First correct: introduced -> learning, not graduated yet - the only word in the lesson
        // must still come back around as a Practice card.
        var result = (repo.submitSelfGrade(session.sessionId, session.card.itemId, correct = true) as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        assertIs<VocabularyCard.Practice>(result.next!!.card)

        // Second correct: learning -> review - graduated, so the session has nothing left to show.
        result = (repo.submitSelfGrade(result.next!!.sessionId, "v0", correct = true) as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("review", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        assertNull(result.next)
    }

    @Test
    fun `keep showing does not touch SRS state and requeues the word`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitStartLearning(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!

        val result = (repo.submitKeepShowing(session.sessionId, session.card.itemId) as ApiResult.Success).data
        assertNull(result.correct)
        assertEquals("introduced", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        assertEquals(0L, db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().correct_streak)
        assertIs<VocabularyCard.Practice>(result.next!!.card)
        assertEquals("v0", result.next!!.card.itemId)
    }

    @Test
    fun `a wrong self-graded answer on a due review demotes and repeats later, not immediately next`() = runTest {
        val (repo, db) = setup(itemCount = 3)
        // v0 already due for review.
        db.vocabularyQueries.updateProgress("review", 2L, 0L, 1L, 0L, nowMillis(), LOCAL_USER, "v0")

        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertIs<VocabularyCard.Practice>(session.card)
        assertEquals("v0", session.card.itemId)

        val result = (repo.submitSelfGrade(session.sessionId, "v0", correct = false) as ApiResult.Success).data
        assertEquals(false, result.correct)
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        assertEquals(0L, db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().interval_index)

        // The very next card must NOT be the repeated word - other words' New cards come first.
        val nextCard = result.next!!.card
        assertTrue(nextCard.itemId != "v0", "repeat card must not appear immediately next")

        // Drain forward until v0 resurfaces.
        var current = result.next!!
        var sawRepeat = false
        var guard = 0
        while (guard < 30 && !sawRepeat) {
            guard++
            val card = current.card
            if (card.itemId == "v0") sawRepeat = true
            val answerResult = when (card) {
                is VocabularyCard.New -> (repo.submitStartLearning(current.sessionId, card.itemId) as ApiResult.Success).data
                is VocabularyCard.Practice -> (repo.submitSelfGrade(current.sessionId, card.itemId, correct = true) as ApiResult.Success).data
            }
            current = answerResult.next ?: break
        }
        assertTrue(sawRepeat, "v0's repeated practice card never resurfaced")
    }

    @Test
    fun `typed and multiple-choice checks grade the same way a self-graded swipe would`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitStartLearning(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!

        var result = (repo.submitChoice(session.sessionId, "v0", "der Wort0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("der Wort0", result.correctAnswer)
        assertEquals("learning", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)

        result = (repo.submitTyped(result.next!!.sessionId, "v0", "der Wort0") as ApiResult.Success).data
        assertEquals(true, result.correct)
        assertEquals("review", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        assertNull(result.next)
    }

    @Test
    fun `complete records session stats and daily activity`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        var session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        session = (repo.submitStartLearning(session.sessionId, session.card.itemId) as ApiResult.Success).data.next!!
        var result = (repo.submitSelfGrade(session.sessionId, "v0", correct = true) as ApiResult.Success).data
        result = (repo.submitSelfGrade(result.next!!.sessionId, "v0", correct = true) as ApiResult.Success).data

        val summary = (repo.complete(session.sessionId, "2026-08-05") as ApiResult.Success).data
        assertEquals(1, summary.wordsLearned)
        assertEquals(100.0, summary.accuracy)
        assertTrue(db.activityQueries.forDate(LOCAL_USER, "2026-08-05").executeAsOneOrNull() != null)
    }

    @Test
    fun `a due-for-review word opens straight on a practice card`() = runTest {
        val (repo, db) = setup(itemCount = 1)
        // Fast-forward v0 straight to "review" status, due now - simulating a word already seen in
        // an earlier session, rather than walking through New/Learning to get there.
        db.vocabularyQueries.updateProgress("review", 2L, 0L, 1L, 0L, nowMillis(), LOCAL_USER, "v0")

        val session = (repo.startOrResumeSession("l1") as ApiResult.Success).data
        assertIs<VocabularyCard.Practice>(session.card)
        assertEquals("v0", session.card.itemId)
    }

    @Test
    fun `updateFlags toggles bookmark`() = runTest {
        val (repo, _) = setup(itemCount = 1)
        val updated = (repo.updateFlags("v0", isBookmarked = true, markedDifficult = null) as ApiResult.Success).data
        assertTrue(updated.isBookmarked)
        assertTrue(!updated.markedDifficult)
    }

    @Test
    fun `a category session pulls only words in that bucket, across the whole goal, as practice cards`() = runTest {
        val (repo, db) = setup(itemCount = 4)
        // v0 struggling (wrong last answer), v1 mastered clean, v2 touched but not struggling/mastered
        // (learning bucket), v3 stays "unseen" (never studied) - the same red/yellow/green split Home
        // shows (see LocalGoalsRepository.vocabularyBreakdown); "unseen" isn't a bucket of its own.
        db.vocabularyQueries.updateProgress("learning", 0L, 1L, 0L, 0L, nowMillis(), LOCAL_USER, "v0")
        db.vocabularyQueries.updateProgress("mastered", 3L, 0L, 4L, 0L, nowMillis(), LOCAL_USER, "v1")
        db.vocabularyQueries.updateProgress("learning", 1L, 0L, 1L, 0L, nowMillis(), LOCAL_USER, "v2")

        val struggling = (repo.startCategorySession("g1", WordCategory.STRUGGLING) as ApiResult.Success).data
        assertIs<VocabularyCard.Practice>(struggling.card)
        assertEquals("v0", struggling.card.itemId)
        assertEquals(1, struggling.totalWords)

        val mastered = (repo.startCategorySession("g1", WordCategory.MASTERED) as ApiResult.Success).data
        assertEquals("v1", mastered.card.itemId)

        val learning = (repo.startCategorySession("g1", WordCategory.LEARNING) as ApiResult.Success).data
        assertEquals("v2", learning.card.itemId)
        assertEquals(1, learning.totalWords)
    }

    @Test
    fun `a category session fails when the bucket is empty`() = runTest {
        val (repo, _) = setup(itemCount = 1)
        // The single word is "unseen" - not struggling, not mastered, and not "learning" either,
        // since "learning" requires having been touched at least once.
        val struggling = repo.startCategorySession("g1", WordCategory.STRUGGLING)
        assertIs<ApiResult.Failure>(struggling)
        val learning = repo.startCategorySession("g1", WordCategory.LEARNING)
        assertIs<ApiResult.Failure>(learning)
    }
}
