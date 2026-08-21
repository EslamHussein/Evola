package evola.composeapp.feature.materials.vm

import evola.composeapp.core.database.testAppDatabase
import evola.database.entity.GoalEntity
import evola.database.entity.MaterialEntity
import evola.shared.core.network.AnthropicClient
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.materials.data.LocalMaterialsRepository
import evola.shared.feature.materials.domain.MaterialsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.testWithInternalState
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory Room database. Seeds a material already in a terminal status
 * (READY/FAILED) so the very first poll tick resolves without needing to manipulate virtual time -
 * [ProcessingViewModel]'s poll loop terminates itself on the first tick in every one of these
 * cases, so no `cancelAndIgnoreRemainingItems()` is needed (compare [evola.composeapp.feature.materials.
 * vm.MaterialDetailViewModelTest]'s `retry` test, which does exercise that path). Robolectric only
 * because Room's Android database builder needs a real `Context`. */
@RunWith(RobolectricTestRunner::class)
class ProcessingViewModelTest {

    private suspend fun repository(materialId: String, status: String): MaterialsRepository {
        val db = testAppDatabase()
        db.goalDao().insert(GoalEntity("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L))
        db.materialDao().insert(MaterialEntity(materialId, LOCAL_USER, "g1", "f.pdf", "h", status, "application/pdf", 1L, null, "auto", null, null, "txt", 0L, 0L, 0L))
        val client = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val fileTextExtractor = object : FileTextExtractor {
            override fun extractText(bytes: ByteArray, mimeType: String): String? = null
        }
        return LocalMaterialsRepository(
            db = db,
            fileTextExtractor = fileTextExtractor,
            segmentation = SegmentationExtractor(client),
            vocabExtractor = VocabularyExtractor(client),
            grammarExtractor = GrammarExtractor(client),
            imageTranscriber = ImageTranscriber(client),
            scope = CoroutineScope(SupervisorJob()),
        )
    }

    @Test
    fun `a material already READY resolves to Done on the first poll`() = runTest {
        val viewModel = ProcessingViewModel("m1", repository("m1", "READY"))
        viewModel.testWithInternalState(this, ProcessingState.Loading) {
            runOnCreate()
            val done = assertIs<ProcessingState.Done>(awaitInternalState())
            assertEquals("m1", done.materialId)
        }
    }

    @Test
    fun `a material already FAILED resolves to Done too - Resource Details renders the failure, not this screen`() = runTest {
        val viewModel = ProcessingViewModel("m1", repository("m1", "FAILED"))
        viewModel.testWithInternalState(this, ProcessingState.Loading) {
            runOnCreate()
            assertIs<ProcessingState.Done>(awaitInternalState())
        }
    }

    @Test
    fun `an unknown material id surfaces as Error, not a crash`() = runTest {
        val viewModel = ProcessingViewModel("does-not-exist", repository("m1", "READY"))
        viewModel.testWithInternalState(this, ProcessingState.Loading) {
            runOnCreate()
            val error = assertIs<ProcessingState.Error>(awaitInternalState())
            assertTrue(error.message.isNotBlank())
        }
    }
}
