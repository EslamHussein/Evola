package evola.composeapp.lessons

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.local.LOCAL_USER
import evola.shared.local.LocalLessonsRepository
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeViewModelTest]: a real [LocalLessonsRepository]
 * backed by an in-memory SQLite [EvolaDatabase]. Seeds a lesson already in a terminal ("ready")
 * status so the very first poll tick resolves without needing to manipulate virtual time - the
 * poll loop itself (re-fetching while "pending") is [LocalLessonsRepository]'s own concern, already
 * exercised indirectly by every other ViewModel that shares this exact poll-until-terminal shape. */
class LessonDetailViewModelTest {

    private fun setup(vocabCount: Int = 2, status: String = "ready"): LocalLessonsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", status, null, 0L)
        repeat(vocabCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        return LocalLessonsRepository(db)
    }

    @Test
    fun `a ready lesson loads with its real vocabulary count and section list`() = runTest {
        val viewModel = LessonDetailViewModel("l1", setup(vocabCount = 3))
        viewModel.testWithInternalState(this, LessonDetailState.Loading) {
            runOnCreate()
            val loaded = assertIs<LessonDetailState.Loaded>(awaitInternalState())
            assertEquals("Lesson 1", loaded.detail.title)
            assertEquals("ready", loaded.detail.status)
            val vocabSection = loaded.detail.sections.single { it.key == "vocabulary" }
            assertEquals("3 words", vocabSection.subtitle)
            assertTrue(vocabSection.locked.not())
            // Every non-vocabulary section is honestly locked - this app doesn't fake sections it hasn't built yet.
            assertTrue(loaded.detail.sections.filter { it.key != "vocabulary" }.all { it.locked })
        }
    }

    @Test
    fun `an unknown lesson id surfaces as Error, not a crash`() = runTest {
        val viewModel = LessonDetailViewModel("does-not-exist", setup())
        viewModel.testWithInternalState(this, LessonDetailState.Loading) {
            runOnCreate()
            val error = assertIs<LessonDetailState.Error>(awaitInternalState())
            assertTrue(error.message.isNotBlank())
        }
    }
}
