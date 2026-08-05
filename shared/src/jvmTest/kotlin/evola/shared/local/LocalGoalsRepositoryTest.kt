package evola.shared.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.ApiResult
import evola.shared.db.EvolaDatabase
import evola.shared.goals.CreateGoalResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LocalGoalsRepositoryTest {

    private fun repo(): Pair<LocalGoalsRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        return LocalGoalsRepository(db) to db
    }

    @Test
    fun `create then read active goal`() = runTest {
        val (r, _) = repo()
        val created = r.createGoal("Pass the German B1 exam", null)
        assertIs<CreateGoalResult.Success>(created)
        assertEquals("Pass the German B1 exam", created.goal.goalText)

        val active = (r.getActiveGoal() as ApiResult.Success).data
        assertEquals("Pass the German B1 exam", active?.goalText)
    }

    @Test
    fun `getProgress is an honest zero state with no lessons`() = runTest {
        val (r, _) = repo()
        val created = r.createGoal("Learn German", null) as CreateGoalResult.Success
        val progress = (r.getProgress(created.goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(0f, progress.overallPct)
        assertNull(progress.currentLessonId)
        assertEquals(0, progress.streakDays)
        assertEquals(false, progress.todayCompleted)
    }

    @Test
    fun `progress reflects graded practice (partial credit) and streak`() = runTest {
        val (r, db) = repo()
        val goal = (r.createGoal("Learn German", null) as CreateGoalResult.Success).goal
        // one material + lesson + one vocab item, practiced correctly on all 5 gradable stages (2..6)
        db.materialsQueries.insert("m1", LOCAL_USER, goal.id, "f.pdf", "h", "READY", "application/pdf", 1L, null, "entire", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", goal.id, 1L, "Lesson 1", "ready", null, 0L)
        db.vocabularyQueries.insertItem("v1", "l1", "Hund", "dog", null, null, null, null, null, null, null, null, null, null, null, 0L)
        db.vocabularyQueries.insertProgress("p1", LOCAL_USER, "v1", "new", 0L, 0L, 0L, null, 0L, 0L)
        db.vocabularyQueries.insertPack("pk1", LOCAL_USER, "l1", 1L, 0L, 1L)
        db.vocabularyQueries.insertPackWord("pw1", "pk1", "v1", 0L, null)
        for (stage in 2..6) db.vocabularyQueries.insertStageAnswer("sa$stage", "pw1", stage.toLong(), "x", 1L, 0L)
        db.activityQueries.upsert("a1", LOCAL_USER, "2026-08-05")

        val progress = (r.getProgress(goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(1f, progress.overallPct) // 5 of 5 gradable stages correct → 100%
        assertNull(progress.currentLessonId) // the only lesson is 100% complete
        assertEquals(1, progress.streakDays)
        assertEquals(true, progress.todayCompleted)
    }

    @Test
    fun `partial graded practice yields partial progress`() = runTest {
        val (r, db) = repo()
        val goal = (r.createGoal("Learn German", null) as CreateGoalResult.Success).goal
        db.materialsQueries.insert("m1", LOCAL_USER, goal.id, "f.pdf", "h", "READY", "application/pdf", 1L, null, "entire", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", goal.id, 1L, "Lesson 1", "ready", null, 0L)
        db.vocabularyQueries.insertItem("v1", "l1", "Hund", "dog", null, null, null, null, null, null, null, null, null, null, null, 0L)
        db.vocabularyQueries.insertProgress("p1", LOCAL_USER, "v1", "new", 0L, 0L, 0L, null, 0L, 0L)
        db.vocabularyQueries.insertPack("pk1", LOCAL_USER, "l1", 1L, 0L, 1L)
        db.vocabularyQueries.insertPackWord("pw1", "pk1", "v1", 0L, null)
        // 3 of 5 gradable stages correct, 2 wrong → 60% for the word (and the lesson)
        for (stage in 2..4) db.vocabularyQueries.insertStageAnswer("ok$stage", "pw1", stage.toLong(), "x", 1L, 0L)
        for (stage in 5..6) db.vocabularyQueries.insertStageAnswer("no$stage", "pw1", stage.toLong(), "x", 0L, 0L)

        val progress = (r.getProgress(goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(0.6f, progress.overallPct)
        assertEquals("l1", progress.currentLessonId) // < 100%, so it's the current lesson
    }
}
