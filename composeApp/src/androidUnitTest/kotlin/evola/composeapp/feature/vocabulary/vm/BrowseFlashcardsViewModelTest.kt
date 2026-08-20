package evola.composeapp.feature.vocabulary.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.profile.data.LocalSettingsRepository
import evola.shared.feature.vocabulary.data.LocalVocabularyRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** [BrowseFlashcardsViewModel] is a plain local flip-through of [LocalVocabularyRepository.listVocabulary]
 * with no repository writes at all - these tests exercise the local index math (next/previous, clamped
 * at both ends) against a real vocabulary repository, same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]. */
class BrowseFlashcardsViewModelTest {

    private fun vocabularyRepository(itemCount: Int, lessonId: String = "l1"): LocalVocabularyRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert("m1", LOCAL_USER, "g1", "f.pdf", "h", "READY", "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        db.lessonsQueries.insert(lessonId, "m1", "g1", 1L, "Lesson 1", "ready", null, 0L)
        repeat(itemCount) { i ->
            val id = "v$i"
            db.vocabularyQueries.insertItem(
                id, lessonId, "Wort$i", "word$i", "der", "Das Wort$i ist gut.",
                null, null, null, null, null, null, null, null, null, null, null, 0L,
            )
            db.vocabularyQueries.insertProgress("p$i", LOCAL_USER, id, "unseen", 0L, 0L, 0L, 0L, null, 0L, 0L)
        }
        val anthropic = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        return LocalVocabularyRepository(db, anthropic, LocalSettingsRepository(db))
    }

    @Test
    fun `loads every word in the lesson and starts on the first one`() = runTest {
        val repository = vocabularyRepository(itemCount = 3)
        val viewModel = BrowseFlashcardsViewModel("l1", repository)

        viewModel.testWithInternalState(this, BrowseFlashcardsState.Loading) {
            runOnCreate()
            val browsing = assertIs<BrowseFlashcardsState.Browsing>(awaitInternalState())
            assertEquals(3, browsing.items.size)
            assertEquals(0, browsing.index)
            assertEquals("Wort0", browsing.items.first().term)
        }
    }

    @Test
    fun `Next and Previous move the index and clamp at both ends`() = runTest {
        val repository = vocabularyRepository(itemCount = 3)
        val viewModel = BrowseFlashcardsViewModel("l1", repository)

        viewModel.testWithInternalState(this, BrowseFlashcardsState.Loading) {
            runOnCreate()
            awaitInternalState()

            // Previous at index 0 is a no-op - it never reduces, so there's nothing new to await.
            containerHost.previous()

            containerHost.next()
            assertEquals(1, assertIs<BrowseFlashcardsState.Browsing>(awaitInternalState()).index)
            containerHost.next()
            assertEquals(2, assertIs<BrowseFlashcardsState.Browsing>(awaitInternalState()).index)

            // Next at the last index is a no-op too - stays clamped at 2, not 3 (out of bounds).
            containerHost.next()
        }
    }

    @Test
    fun `a lesson with no vocabulary yields Empty, not a crash`() = runTest {
        val repository = vocabularyRepository(itemCount = 0)
        val viewModel = BrowseFlashcardsViewModel("l1", repository)

        viewModel.testWithInternalState(this, BrowseFlashcardsState.Loading) {
            runOnCreate()
            assertIs<BrowseFlashcardsState.Empty>(awaitInternalState())
        }
    }
}
