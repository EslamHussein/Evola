package evola.shared.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Smoke test: the generated schema creates cleanly on an in-memory SQLite DB and a representative
 * round-trip (insert goal → read active) works — validates the .sq port before any repository
 * depends on it. Lives in jvmTest so it runs on the JVM with the JDBC driver. */
class EvolaDatabaseTest {

    private fun freshDb(): EvolaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        return EvolaDatabase(driver)
    }

    @Test
    fun `schema creates and a goal round-trips`() {
        val db = freshDb()
        db.goalsQueries.insert(
            id = "g1",
            user_id = "local",
            goal_text = "Pass the German B1 exam",
            title = "My journey",
            is_active = 1,
            created_at = 1_000L,
            updated_at = 1_000L,
        )
        val active = db.goalsQueries.selectActive("local").executeAsOne()
        assertEquals("Pass the German B1 exam", active.goal_text)
        assertEquals("My journey", active.title)
    }

    @Test
    fun `daily_activity upsert is idempotent on the same date`() {
        val db = freshDb()
        db.activityQueries.upsert("a1", "local", "2026-08-05")
        db.activityQueries.upsert("a2", "local", "2026-08-05")
        assertEquals(listOf("2026-08-05"), db.activityQueries.completedDates("local").executeAsList())
    }

    @Test
    fun `no active goal returns null`() {
        val db = freshDb()
        assertNull(db.goalsQueries.selectActive("local").executeAsOneOrNull())
    }
}
