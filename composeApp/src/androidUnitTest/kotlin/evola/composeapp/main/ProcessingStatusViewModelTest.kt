package evola.composeapp.main

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.ai.GrammarExtractor
import evola.shared.ai.ImageTranscriber
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.LOCAL_USER
import evola.shared.local.LocalMaterialsRepository
import evola.shared.materials.MaterialStatus
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** [ProcessingStatusViewModel] polls [evola.shared.materials.MaterialsRepository.list] in a
 * `while(true)` loop via `onCreate`, so these tests never try to wait out multiple poll ticks -
 * they only assert on the state produced by the very first tick, which runs immediately (before
 * the loop's own `delay`). A real [LocalMaterialsRepository] backs the test (matching this
 * project's "never mock a repository" convention), backed by an in-memory SQLite [EvolaDatabase]
 * with material rows inserted directly - `upload()`/`processMaterial()` are never exercised, so
 * the AI/file-extraction collaborators it requires are given inert real implementations that
 * error loudly if the test accidentally invokes them. */
@OptIn(ExperimentalUuidApi::class)
class ProcessingStatusViewModelTest {

    private fun materialsRepository(db: EvolaDatabase): LocalMaterialsRepository {
        val client = AnthropicClient(MockEngine { error("AI must not be called by this test") }) { "test-key" }
        return LocalMaterialsRepository(
            db = db,
            fileTextExtractor = object : FileTextExtractor {
                override fun extractText(bytes: ByteArray, mimeType: String): String? =
                    error("file extraction must not be called by this test")
            },
            segmentation = SegmentationExtractor(client),
            vocabExtractor = VocabularyExtractor(client),
            grammarExtractor = GrammarExtractor(client),
            imageTranscriber = ImageTranscriber(client),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun database(): EvolaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        return EvolaDatabase(driver)
    }

    private fun seedMaterial(db: EvolaDatabase, goalId: String, status: String, filename: String = "book.pdf"): String {
        val id = Uuid.random().toString()
        db.materialsQueries.insert(
            id, LOCAL_USER, goalId, filename, "hash-$id", status, "application/pdf", 1024L,
            null, "auto", null, null, "some content", Clock.System.now().toEpochMilliseconds(),
        )
        return id
    }

    @Test
    fun `first poll tick surfaces a processing material and filters out a ready one`() = runTest {
        val db = database()
        val processingId = seedMaterial(db, "goal-1", "PROCESSING")
        seedMaterial(db, "goal-1", "READY")

        val viewModel = ProcessingStatusViewModel(materialsRepository(db))
        viewModel.testWithInternalState(this, ProcessingStatusState()) {
            runOnCreate()
            val loaded = awaitInternalState()
            assertEquals(1, loaded.processingMaterials.size)
            assertEquals(processingId, loaded.processingMaterials.first().id)
            assertEquals(MaterialStatus.PROCESSING, loaded.processingMaterials.first().status)
            // onCreate is a `while(true)` poll loop that never completes on its own - cancel it
            // explicitly so the test doesn't hang waiting for it to join.
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `first poll tick surfaces every processing material across goals`() = runTest {
        val db = database()
        val first = seedMaterial(db, "goal-1", "PROCESSING", filename = "a.pdf")
        val second = seedMaterial(db, "goal-2", "PROCESSING", filename = "b.pdf")
        seedMaterial(db, "goal-1", "FAILED")

        val viewModel = ProcessingStatusViewModel(materialsRepository(db))
        viewModel.testWithInternalState(this, ProcessingStatusState()) {
            runOnCreate()
            val loaded = awaitInternalState()
            assertEquals(setOf(first, second), loaded.processingMaterials.map { it.id }.toSet())
            cancelAndIgnoreRemainingItems()
        }
    }
}
