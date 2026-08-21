package evola.database

import evola.database.entity.GoalEntity
import evola.database.entity.DailyActivityEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Smoke test: the Room schema creates cleanly and a representative round-trip (insert goal ->
 * read active) works on every target's own [DatabaseFactory] actual - validates the entity/DAO
 * port before any repository depends on it, same role the old SQLDelight-era EvolaDatabaseTest
 * played. */
class AppDatabaseTest {

    private fun freshDb(): AppDatabase = DatabaseFactory().create()

    @Test
    fun `schema creates and a goal round-trips`() = runTest {
        val db = freshDb()
        db.goalDao().insert(GoalEntity(id = "g1", userId = "local", goalText = "Pass the German B1 exam", title = "My journey", nativeLanguage = "en", isActive = 1L, createdAt = 1000L, updatedAt = 1000L))
        val active = db.goalDao().selectActive("local")!!
        assertEquals("Pass the German B1 exam", active.goalText)
        assertEquals("My journey", active.title)
    }

    @Test
    fun `daily_activity upsert is idempotent on the same date`() = runTest {
        val db = freshDb()
        db.activityDao().upsert(DailyActivityEntity("a1", "local", "2026-08-05", 1L))
        db.activityDao().upsert(DailyActivityEntity("a2", "local", "2026-08-05", 1L))
        assertEquals(listOf("2026-08-05"), db.activityDao().completedDates("local"))
    }

    @Test
    fun `no active goal returns null`() = runTest {
        val db = freshDb()
        assertNull(db.goalDao().selectActive("local"))
    }
}
