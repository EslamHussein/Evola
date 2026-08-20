package evola.composeapp.feature.learning.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.learning.data.LocalGrammarRepository
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeViewModelTest]: a real [LocalGrammarRepository]
 * backed by an in-memory SQLite [EvolaDatabase], driven through [GrammarTopicListViewModel] via
 * the official `org.orbit-mvi:orbit-test` DSL. */
class GrammarTopicListViewModelTest {

    private fun setup(): LocalGrammarRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        db.grammarQueries.insertTopic("t1", "l1", "Akkusativ", "The accusative case", 0L)
        db.grammarQueries.insertTopicProgress("gp1", LOCAL_USER, "t1", "new", 0L, 0L, 0L, null)
        db.grammarQueries.insertTopic("t2", "l1", "Dativ", "The dative case", 1L)
        db.grammarQueries.insertTopicProgress("gp2", LOCAL_USER, "t2", "learning", 0L, 0L, 0L, null)
        return LocalGrammarRepository(db)
    }

    @Test
    fun `loads every grammar topic in the lesson`() = runTest {
        val viewModel = GrammarTopicListViewModel("l1", setup())
        viewModel.testWithInternalState(this, GrammarTopicListState.Loading) {
            runOnCreate()
            val loaded = assertIs<GrammarTopicListState.Loaded>(awaitInternalState())
            assertEquals(2, loaded.topics.size)
            assertEquals(setOf("Akkusativ", "Dativ"), loaded.topics.map { it.name }.toSet())
            assertTrue(loaded.topics.any { it.masteryState == "new" })
            assertTrue(loaded.topics.any { it.masteryState == "learning" })
        }
    }

    @Test
    fun `a lesson with no grammar topics still loads as an empty list, not a crash`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)

        val viewModel = GrammarTopicListViewModel("l1", LocalGrammarRepository(db))
        viewModel.testWithInternalState(this, GrammarTopicListState.Loading) {
            runOnCreate()
            val loaded = assertIs<GrammarTopicListState.Loaded>(awaitInternalState())
            assertEquals(0, loaded.topics.size)
        }
    }
}
