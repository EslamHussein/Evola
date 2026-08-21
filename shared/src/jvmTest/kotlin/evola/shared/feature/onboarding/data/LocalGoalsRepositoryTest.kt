package evola.shared.feature.onboarding.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.database.DatabaseFactory
import evola.database.create
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import evola.shared.db.EvolaDatabase
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.feature.profile.data.LocalAchievementsRepository
import evola.shared.feature.profile.data.LocalSettingsRepository
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
        val roomDb = DatabaseFactory().create()
        return LocalGoalsRepository(db, LocalSettingsRepository(db), LocalAchievementsRepository(roomDb)) to db
    }

    @Test
    fun `create then read active goal`() = runTest {
        val (r, _) = repo()
        val created = r.createGoal("Pass the German B1 exam", null, NativeLanguage.ENGLISH)
        assertIs<CreateGoalResult.Success>(created)
        assertEquals("Pass the German B1 exam", created.goal.goalText)

        val active = (r.getActiveGoal() as ApiResult.Success).data
        assertEquals("Pass the German B1 exam", active?.goalText)
    }

    @Test
    fun `getProgress is an honest zero state with no lessons`() = runTest {
        val (r, _) = repo()
        val created = r.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success
        val progress = (r.getProgress(created.goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(0f, progress.overallPct)
        assertNull(progress.currentLessonId)
        assertEquals(0, progress.streakDays)
        assertEquals(false, progress.todayCompleted)
    }

    @Test
    fun `progress reflects a fully mastered lesson and streak`() = runTest {
        val (r, db) = repo()
        val goal = (r.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success).goal
        // one material + lesson + one vocab item, already at the top of the 5-status ladder
        db.materialsQueries.insert("m1", LOCAL_USER, goal.id, "f.pdf", "h", "READY", "application/pdf", 1L, null, "entire", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", goal.id, 1L, "Lesson 1", "ready", null, 0L)
        db.vocabularyQueries.insertItem("v1", "l1", "Hund", "dog", null, null, null, null, null, null, null, null, null, null, null, null, null, 0L)
        db.vocabularyQueries.insertProgress("p1", LOCAL_USER, "v1", "mastered", 0L, 0L, 0L, 0L, null, 0L, 0L)
        db.activityQueries.upsert("a1", LOCAL_USER, "2026-08-05")

        val progress = (r.getProgress(goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(1f, progress.overallPct) // mastered = the last rung of the 5-status ladder → 100%
        assertNull(progress.currentLessonId) // the only lesson is 100% complete
        assertEquals(1, progress.streakDays)
        assertEquals(true, progress.todayCompleted)
    }

    @Test
    fun `mid-ladder status yields partial progress`() = runTest {
        val (r, db) = repo()
        val goal = (r.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success).goal
        db.materialsQueries.insert("m1", LOCAL_USER, goal.id, "f.pdf", "h", "READY", "application/pdf", 1L, null, "entire", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", goal.id, 1L, "Lesson 1", "ready", null, 0L)
        db.vocabularyQueries.insertItem("v1", "l1", "Hund", "dog", null, null, null, null, null, null, null, null, null, null, null, null, null, 0L)
        // "learning" is index 2 of 5 statuses (unseen=0..mastered=4) → 2/4 = 50%
        db.vocabularyQueries.insertProgress("p1", LOCAL_USER, "v1", "learning", 0L, 0L, 0L, 0L, null, 0L, 0L)

        val progress = (r.getProgress(goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(0.5f, progress.overallPct)
        assertEquals("l1", progress.currentLessonId) // < 100%, so it's the current lesson
    }

    @Test
    fun `weekly activity and today's learned count reflect completed sessions`() = runTest {
        val (r, db) = repo()
        val goal = (r.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success).goal
        db.materialsQueries.insert("m1", LOCAL_USER, goal.id, "f.pdf", "h", "READY", "application/pdf", 1L, null, "entire", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", goal.id, 1L, "Lesson 1", "ready", null, 0L)
        db.settingsQueries.upsert(LOCAL_USER, "daily_new_word_goal", "9")

        db.vocabularyQueries.insertSession("s1", LOCAL_USER, "l1", 1L, 0L, 6L, 2L)
        db.vocabularyQueries.completeSession(0L, "2026-08-05", "s1")
        db.activityQueries.upsert("a1", LOCAL_USER, "2026-08-05")

        val progress = (r.getProgress(goal.id, "2026-08-05") as ApiResult.Success).data
        assertEquals(9, progress.dailyGoal)
        assertEquals(6, progress.todayNewWordsLearned)
        assertEquals(7, progress.weeklyActivity.size)
        val today = progress.weeklyActivity.last()
        assertEquals("2026-08-05", today.date)
        assertEquals(true, today.hadActivity)
        assertEquals(6, today.newWordsLearned)
        assertEquals(2, today.wordsReviewed)
    }
}
