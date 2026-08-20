package evola.composeapp.feature.materials.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.LOCAL_USER
import evola.shared.feature.materials.data.LocalMaterialsRepository
import evola.shared.feature.materials.domain.MaterialStatus
import evola.shared.feature.materials.domain.MaterialsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase], driven through [MaterialDetailViewModel] via the
 * official `org.orbit-mvi:orbit-test` DSL. Every seeded material starts in a terminal status
 * (READY/FAILED), so the poll loop resolves after exactly one tick, same convention as
 * [evola.composeapp.feature.learning.vm.LessonDetailViewModelTest] - except the [retry] test, which
 * deliberately moves the material into the non-terminal PROCESSING status and so must end its
 * `validate` block with `cancelAndIgnoreRemainingItems()` (see the plan's gotcha notes on
 * infinite `onCreate`/poll loops). */
class MaterialDetailViewModelTest {

    private fun setup(materialId: String = "m1", status: String = "READY", lessonCount: Int = 0): Pair<MaterialsRepository, EvolaDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert(materialId, LOCAL_USER, "g1", "f.pdf", "h", status, "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
        repeat(lessonCount) { i ->
            db.lessonsQueries.insert("l$i", materialId, "g1", (i + 1).toLong(), "Lesson ${i + 1}", "ready", null, 0L)
        }
        val client = AnthropicClient(MockEngine { respond("{\"content\":[]}", HttpStatusCode.OK) }) { "sk-test" }
        val fileTextExtractor = object : FileTextExtractor {
            override fun extractText(bytes: ByteArray, mimeType: String): String? = null
        }
        val repository = LocalMaterialsRepository(
            db = db,
            fileTextExtractor = fileTextExtractor,
            segmentation = SegmentationExtractor(client),
            vocabExtractor = VocabularyExtractor(client),
            grammarExtractor = GrammarExtractor(client),
            imageTranscriber = ImageTranscriber(client),
            scope = CoroutineScope(SupervisorJob()),
        )
        return repository to db
    }

    private fun repository(materialId: String = "m1", status: String = "READY", lessonCount: Int = 0): MaterialsRepository =
        setup(materialId, status, lessonCount).first

    @Test
    fun `loads a READY material with its real lesson list`() = runTest {
        val viewModel = MaterialDetailViewModel("m1", repository(lessonCount = 2))
        viewModel.testWithInternalState(this, MaterialDetailState.Loading) {
            runOnCreate()
            val loaded = assertIs<MaterialDetailState.Loaded>(awaitInternalState())
            assertEquals(2, loaded.detail.lessons.size)
        }
    }

    @Test
    fun `DeleteLesson removes exactly the targeted lesson`() = runTest {
        val viewModel = MaterialDetailViewModel("m1", repository(lessonCount = 2))
        viewModel.testWithInternalState(this, MaterialDetailState.Loading) {
            runOnCreate()
            awaitInternalState()

            containerHost.deleteLesson("l0")
            val loaded = assertIs<MaterialDetailState.Loaded>(awaitInternalState())
            assertEquals(1, loaded.detail.lessons.size)
            assertTrue(loaded.detail.lessons.none { it.id == "l0" })
        }
    }

    @Test
    fun `Retry on a FAILED material re-enters PROCESSING`() = runTest {
        val viewModel = MaterialDetailViewModel("m1", repository(status = "FAILED"))
        viewModel.testWithInternalState(this, MaterialDetailState.Loading) {
            runOnCreate()
            awaitInternalState()

            containerHost.retry()
            val processing = assertIs<MaterialDetailState.Loaded>(awaitInternalState())
            assertEquals(MaterialStatus.PROCESSING, processing.detail.material.status)

            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `an unknown material id surfaces as Error, not a crash`() = runTest {
        val viewModel = MaterialDetailViewModel("does-not-exist", repository())
        viewModel.testWithInternalState(this, MaterialDetailState.Loading) {
            runOnCreate()
            val error = assertIs<MaterialDetailState.Error>(awaitInternalState())
            assertTrue(error.message.isNotBlank())
        }
    }
}
