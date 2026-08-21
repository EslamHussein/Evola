package evola.composeapp.feature.home.vm

import evola.composeapp.core.database.testAppDatabase
import evola.database.AppDatabase
import evola.database.entity.MaterialEntity
import evola.shared.core.network.AnthropicClient
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.materials.data.LocalMaterialsRepository
import evola.shared.feature.materials.domain.MaterialStatus
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.testWithInternalState
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** [ProcessingStatusViewModel] polls [evola.shared.feature.materials.domain.MaterialsRepository.list] in a
 * `while(true)` loop via `onCreate`, so these tests never try to wait out multiple poll ticks -
 * they only assert on the state produced by the very first tick, which runs immediately (before
 * the loop's own `delay`). A real [LocalMaterialsRepository] backs the test (matching this
 * project's "never mock a repository" convention), backed by an in-memory Room [AppDatabase]
 * with material rows inserted directly - `upload()`/`processMaterial()` are never exercised, so
 * the AI/file-extraction collaborators it requires are given inert real implementations that
 * error loudly if the test accidentally invokes them. Robolectric only because Room's Android
 * database builder needs a real `Context`. */
@OptIn(ExperimentalUuidApi::class)
@RunWith(RobolectricTestRunner::class)
class ProcessingStatusViewModelTest {

    private fun materialsRepository(db: AppDatabase): LocalMaterialsRepository {
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

    private suspend fun seedMaterial(db: AppDatabase, goalId: String, status: String, filename: String = "book.pdf"): String {
        val id = Uuid.random().toString()
        db.materialDao().insert(
            MaterialEntity(
                id, LOCAL_USER, goalId, filename, "hash-$id", status, "application/pdf", 1024L,
                null, "auto", null, null, "some content", 0L, 0L, Clock.System.now().toEpochMilliseconds(),
            ),
        )
        return id
    }

    @Test
    fun `first poll tick surfaces a processing material and filters out a ready one`() = runTest {
        val db = testAppDatabase()
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
        val db = testAppDatabase()
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
