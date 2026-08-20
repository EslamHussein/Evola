package evola.composeapp.lessons

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.LOCAL_USER
import evola.shared.local.LocalSettingsRepository
import evola.shared.local.LocalVocabularyRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeViewModelTest]: a real [LocalVocabularyRepository]
 * backed by an in-memory SQLite [EvolaDatabase], driven through [VocabularyListViewModel] via the
 * official `org.orbit-mvi:orbit-test` DSL - never a mocked repository. Seeding follows
 * `LocalVocabularyRepositoryTest`'s own helper pattern (goal/material/lesson/vocabulary-item inserts). */
class VocabularyListViewModelTest {

    private fun setup(itemCount: Int = 3): Pair<LocalVocabularyRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert("l1", "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, "l1", "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        val anthropic = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val settingsRepository = LocalSettingsRepository(db)
        return LocalVocabularyRepository(db, anthropic, settingsRepository) to db
    }

    private fun viewModel(repository: LocalVocabularyRepository, lessonId: String = "l1", goalId: String = "g1") =
        VocabularyListViewModel(lessonId, goalId, repository)

    @Test
    fun `initial load shows every seeded word in the lesson`() = runTest {
        val (repository, _) = setup(itemCount = 3)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertEquals(3, loaded.items.size)
            assertEquals(setOf("Wort0", "Wort1", "Wort2"), loaded.items.map { it.term }.toSet())
        }
    }

    @Test
    fun `UpdateItem changes term and meaning in state and fires ItemUpdated`() = runTest {
        val (repository, _) = setup(itemCount = 1)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.updateItem("v0", "Buch", "book", "das Buch")
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            val item = loaded.items.single { it.itemId == "v0" }
            assertEquals("Buch", item.term)
            assertEquals("book", item.meaning)
            assertEquals("das Buch", item.nativeMeaning)

            val effect = assertIs<VocabularyListSideEffect.ItemUpdated>(awaitSideEffect())
            assertEquals("Buch", effect.item.term)
        }
    }

    @Test
    fun `MarkAlreadyKnown moves the item to review status in place`() = runTest {
        val (repository, db) = setup(itemCount = 1)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.markAlreadyKnown("v0")
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            val item = loaded.items.single { it.itemId == "v0" }
            assertEquals("review", item.status)

            val effect = assertIs<VocabularyListSideEffect.MarkedAlreadyKnown>(awaitSideEffect())
            assertEquals("v0", effect.item?.itemId)
            assertEquals("review", db.vocabularyQueries.progressForItem(LOCAL_USER, "v0").executeAsOne().status)
        }
    }

    @Test
    fun `AddWord appends a new item to the loaded list`() = runTest {
        val (repository, _) = setup(itemCount = 2)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.addWord("Haus", "house", "das Haus")
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertEquals(3, loaded.items.size)
            val added = loaded.items.last()
            assertEquals("Haus", added.term)
            assertEquals("house", added.meaning)

            val effect = assertIs<VocabularyListSideEffect.WordAdded>(awaitSideEffect())
            assertTrue(effect.success)
        }
    }

    @Test
    fun `DeleteItem removes exactly the targeted item`() = runTest {
        val (repository, _) = setup(itemCount = 3)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.deleteItem("v1")
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertEquals(2, loaded.items.size)
            assertTrue(loaded.items.none { it.itemId == "v1" })
            assertEquals(setOf("v0", "v2"), loaded.items.map { it.itemId }.toSet())

            val effect = assertIs<VocabularyListSideEffect.ItemDeleted>(awaitSideEffect())
            assertTrue(effect.success)
        }
    }

    @Test
    fun `ResetProgress clears every word's status back to unseen and re-fetches`() = runTest {
        val (repository, db) = setup(itemCount = 2)
        db.vocabularyQueries.updateProgress("mastered", 3L, 0L, 4L, 0L, 0L, LOCAL_USER, "v0")

        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.resetProgress()
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertTrue(loaded.items.all { it.status == "unseen" })

            val effect = assertIs<VocabularyListSideEffect.ProgressReset>(awaitSideEffect())
            assertTrue(effect.success)
        }
    }

    @Test
    fun `ImportWords bulk-adds rows and re-fetches the full list`() = runTest {
        val (repository, _) = setup(itemCount = 1)
        val rows = listOf(
            Triple("Katze", "cat", "die Katze" as String?),
            Triple("Hund", "dog", null as String?),
        )
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            runOnCreate()
            awaitInternalState()
            containerHost.importWords(rows)
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertEquals(3, loaded.items.size)
            assertTrue(loaded.items.any { it.term == "Katze" && it.meaning == "cat" })
            assertTrue(loaded.items.any { it.term == "Hund" && it.meaning == "dog" })

            val effect = assertIs<VocabularyListSideEffect.WordsImported>(awaitSideEffect())
            assertEquals(2, effect.count)
        }
    }

    @Test
    fun `MarkAlreadyKnown on an unknown item fails gracefully without crashing state`() = runTest {
        val (repository, _) = setup(itemCount = 1)
        viewModel(repository).testWithInternalState(this, VocabularyListState()) {
            // The failure branch never reduces, so there's no post-failure internal-state item to
            // await - load the list first so a "still loaded, still one item" comparison is meaningful.
            runOnCreate()
            val loaded = assertIs<VocabularyListContent.Loaded>(awaitInternalState().content)
            assertEquals(1, loaded.items.size)

            containerHost.markAlreadyKnown("does-not-exist")
            val effect = assertIs<VocabularyListSideEffect.MarkedAlreadyKnown>(awaitSideEffect())
            assertNull(effect.item)
        }
    }
}
