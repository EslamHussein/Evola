package evola.shared.feature.learning.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import evola.shared.db.EvolaDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalGrammarRepositoryTest {

    private fun setup(): Pair<LocalGrammarRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        // goal + material + lesson + one topic (progress 'new') + two fill_in_blank exercises
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        db.grammarQueries.insertTopic("t1", "l1", "Akkusativ", "The accusative case", 0L)
        db.grammarQueries.insertTopicProgress("gp1", LOCAL_USER, "t1", "new", 0L, 0L, 0L, null)
        db.grammarQueries.insertExercise("e1", "t1", "fill_in_blank", "Ich sehe ___ Hund", "den", null, 0L)
        db.grammarQueries.insertExercise("e2", "t1", "fill_in_blank", "Er hat ___ Ball", "den", null, 1L)
        return LocalGrammarRepository(db) to db
    }

    @Test
    fun `list topics and start a session`() = runTest {
        val (repo, _) = setup()
        val topics = (repo.listTopics("l1") as ApiResult.Success).data
        assertEquals(1, topics.size)
        assertEquals("Akkusativ", topics.first().name)

        val session = (repo.startOrResumeSession("t1") as ApiResult.Success).data
        assertEquals("Akkusativ", session.topicName)
        assertEquals(2, session.exercises.size)
    }

    @Test
    fun `two consecutive correct answers advance mastery, idempotent re-answer`() = runTest {
        val (repo, _) = setup()
        val session = (repo.startOrResumeSession("t1") as ApiResult.Success).data

        // first correct → partial only (streak 0→1), mastery still "new"
        val first = (repo.answer(session.sessionId, "e1", "den", correct = true) as ApiResult.Success).data
        assertEquals("new", first.masteryState)

        // second consecutive correct → onCorrect, mastery advances to "learning"
        val second = (repo.answer(session.sessionId, "e2", "den", correct = true) as ApiResult.Success).data
        assertEquals("learning", second.masteryState)

        // idempotency: re-answering e1 returns its stored snapshot, doesn't re-advance
        val replay = (repo.answer(session.sessionId, "e1", "den", correct = true) as ApiResult.Success).data
        assertEquals("new", replay.masteryState)
    }

    @Test
    fun `complete computes accuracy and records the day`() = runTest {
        val (repo, db) = setup()
        val session = (repo.startOrResumeSession("t1") as ApiResult.Success).data
        repo.answer(session.sessionId, "e1", "den", correct = true)
        repo.answer(session.sessionId, "e2", "wrong", correct = false)

        val summary = (repo.complete(session.sessionId, "2026-08-05") as ApiResult.Success).data
        assertEquals(2, summary.exercisesCompleted)
        assertEquals(50.0, summary.accuracy)
        assertTrue(db.activityQueries.forDate(LOCAL_USER, "2026-08-05").executeAsOneOrNull() != null)
    }
}
