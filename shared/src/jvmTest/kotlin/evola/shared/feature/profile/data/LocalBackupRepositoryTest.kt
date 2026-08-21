package evola.shared.feature.profile.data

import evola.database.AppDatabase
import evola.database.DatabaseFactory
import evola.database.create
import evola.database.entity.GoalEntity
import evola.database.entity.LessonEntity
import evola.database.entity.MaterialEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.database.entity.UserSettingEntity
import evola.database.entity.DailyActivityEntity
import evola.shared.core.common.ApiResult
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.profile.domain.BackupRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalBackupRepositoryTest {

    private suspend fun setup(): Pair<BackupRepository, AppDatabase> {
        val db = DatabaseFactory().create()
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "My Journey", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        db.lessonDao().insert(LessonEntity("l1", "m1", "g1", 1L, "Lesson 1", "ready", "curriculum", null, null, 0L))
        db.vocabularyDao().insertItem(VocabularyItemEntity("v1", "l1", "Hund", "dog", "der", "Der Hund läuft.", null, null, null, null, "dog", null, null, null, null, null, null, null, 0L))
        db.vocabularyDao().insertProgress(VocabularyProgressEntity("p1", LOCAL_USER, "v1", "learning", 2L, 0L, 1L, 12345L, 100L, 1L, 0L))
        db.activityDao().upsert(DailyActivityEntity("a1", LOCAL_USER, "2026-08-10", 1L))
        db.settingsDao().upsert(UserSettingEntity(LOCAL_USER, "daily_new_word_goal", "12"))
        return LocalBackupRepository(db) to db
    }

    @Test
    fun `export then import round-trips every covered table`() = runTest {
        val (backup, db) = setup()
        val json = backup.export()

        val result = backup.import(json)
        assertIs<ApiResult.Success<Unit>>(result)

        assertEquals("Learn German", db.goalDao().selectActive(LOCAL_USER)!!.goalText)
        assertEquals("Hund", db.vocabularyDao().itemById("v1")!!.term)
        val progress = db.vocabularyDao().progressForItem(LOCAL_USER, "v1")!!
        assertEquals("learning", progress.status)
        assertEquals(1L, progress.isBookmarked)
        assertTrue(db.activityDao().forDate(LOCAL_USER, "2026-08-10") != null)
        assertEquals("12", db.settingsDao().get(LOCAL_USER, "daily_new_word_goal"))
    }

    @Test
    fun `import rejects malformed json`() = runTest {
        val (backup, _) = setup()
        val result = backup.import("not valid json")
        assertIs<ApiResult.Failure>(result)
    }

    @Test
    fun `import rejects a mismatched schema version`() = runTest {
        val (backup, _) = setup()
        val json = backup.export().replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val result = backup.import(json)
        assertIs<ApiResult.Failure>(result)
    }
}
