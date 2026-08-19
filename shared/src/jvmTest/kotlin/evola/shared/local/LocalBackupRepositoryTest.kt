package evola.shared.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.ApiResult
import evola.shared.db.EvolaDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalBackupRepositoryTest {

    private fun setup(): Pair<BackupRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "My Journey", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        db.vocabularyQueries.insertItem("v1", "l1", "Hund", "dog", "der", "Der Hund läuft.", null, null, null, null, "dog", null, null, null, null, null, null, 0L)
        db.vocabularyQueries.insertProgress("p1", LOCAL_USER, "v1", "learning", 2L, 0L, 1L, 12345L, 100L, 1L, 0L)
        db.activityQueries.upsert("a1", LOCAL_USER, "2026-08-10")
        db.settingsQueries.upsert(LOCAL_USER, "daily_new_word_goal", "12")
        return LocalBackupRepository(db) to db
    }

    @Test
    fun `export then import round-trips every covered table`() {
        val (backup, db) = setup()
        val json = backup.export()

        val result = backup.import(json)
        assertIs<ApiResult.Success<Unit>>(result)

        assertEquals("Learn German", db.goalsQueries.selectActive(LOCAL_USER).executeAsOne().goal_text)
        assertEquals("Hund", db.vocabularyQueries.itemById("v1").executeAsOne().term)
        val progress = db.vocabularyQueries.progressForItem(LOCAL_USER, "v1").executeAsOne()
        assertEquals("learning", progress.status)
        assertEquals(1L, progress.is_bookmarked)
        assertTrue(db.activityQueries.forDate(LOCAL_USER, "2026-08-10").executeAsOneOrNull() != null)
        assertEquals("12", db.settingsQueries.get(LOCAL_USER, "daily_new_word_goal").executeAsOne())
    }

    @Test
    fun `import rejects malformed json`() {
        val (backup, _) = setup()
        val result = backup.import("not valid json")
        assertIs<ApiResult.Failure>(result)
    }

    @Test
    fun `import rejects a mismatched schema version`() {
        val (backup, _) = setup()
        val json = backup.export().replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val result = backup.import(json)
        assertIs<ApiResult.Failure>(result)
    }
}
