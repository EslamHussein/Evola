package evola.shared.feature.learning.data

import evola.database.AppDatabase
import evola.database.DatabaseFactory
import evola.database.create
import evola.database.entity.GoalEntity
import evola.database.entity.GrammarExerciseEntity
import evola.database.entity.GrammarProgressEntity
import evola.database.entity.GrammarTopicEntity
import evola.database.entity.LessonEntity
import evola.database.entity.MaterialEntity
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalGrammarRepositoryTest {

    private suspend fun setup(): Pair<LocalGrammarRepository, AppDatabase> {
        val db = DatabaseFactory().create()
        // goal + material + lesson + one topic (progress 'new') + two fill_in_blank exercises
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        db.lessonDao().insert(LessonEntity("l1", "m1", "g1", 1L, "Lesson 1", "ready", "curriculum", null, null, 0L))
        db.grammarDao().insertTopic(GrammarTopicEntity("t1", "l1", "Akkusativ", "The accusative case", 0L))
        db.grammarDao().insertTopicProgress(GrammarProgressEntity("gp1", LOCAL_USER, "t1", "new", 0L, 0L, 0L, null))
        db.grammarDao().insertExercise(GrammarExerciseEntity("e1", "t1", "fill_in_blank", "Ich sehe ___ Hund", "den", null, 0L))
        db.grammarDao().insertExercise(GrammarExerciseEntity("e2", "t1", "fill_in_blank", "Er hat ___ Ball", "den", null, 1L))
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
        assertTrue(db.activityDao().forDate(LOCAL_USER, "2026-08-05") != null)
    }
}
