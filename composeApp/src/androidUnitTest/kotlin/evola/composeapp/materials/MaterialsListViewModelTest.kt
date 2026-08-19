package evola.composeapp.materials

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.ai.AnthropicClient
import evola.shared.ai.GrammarExtractor
import evola.shared.ai.ImageTranscriber
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.db.EvolaDatabase
import evola.shared.files.FileTextExtractor
import evola.shared.local.LOCAL_USER
import evola.shared.local.LocalMaterialsRepository
import evola.shared.materials.MaterialsRepository
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

/** Same convention as [evola.composeapp.main.HomeViewModelTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase], driven through [MaterialsListViewModel] via the
 * official `org.orbit-mvi:orbit-test` DSL. Every seeded material is READY (never PROCESSING), so
 * `onCreate`'s poll loop never actually starts polling - no `cancelAndIgnoreRemainingItems()`
 * needed here (compare [MaterialDetailViewModelTest], which does exercise that path). */
class MaterialsListViewModelTest {

    private fun repository(materialCount: Int = 2): MaterialsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        repeat(materialCount) { i ->
            db.materialsQueries.insert(
                "m$i", LOCAL_USER, "g1", "f$i.pdf", "h$i", "READY", "application/pdf", 1L,
                null, "auto", null, null, "txt", i.toLong(),
            )
        }
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
    fun `loads every seeded material`() = runTest {
        val viewModel = MaterialsListViewModel(repository(materialCount = 3))
        viewModel.testWithInternalState(this, MaterialsListState.Loading) {
            runOnCreate()
            val loaded = assertIs<MaterialsListState.Loaded>(awaitInternalState())
            assertEquals(3, loaded.materials.size)
        }
    }

    @Test
    fun `an empty goal loads as an empty list, not a crash`() = runTest {
        val viewModel = MaterialsListViewModel(repository(materialCount = 0))
        viewModel.testWithInternalState(this, MaterialsListState.Loading) {
            runOnCreate()
            val loaded = assertIs<MaterialsListState.Loaded>(awaitInternalState())
            assertTrue(loaded.materials.isEmpty())
        }
    }

    @Test
    fun `Delete removes exactly the targeted material and reloads the rest`() = runTest {
        val viewModel = MaterialsListViewModel(repository(materialCount = 2))
        viewModel.testWithInternalState(this, MaterialsListState.Loading) {
            runOnCreate()
            assertIs<MaterialsListState.Loaded>(awaitInternalState())

            containerHost.delete("m0")
            val loading = awaitInternalState()
            assertIs<MaterialsListState.Loading>(loading)
            val loaded = assertIs<MaterialsListState.Loaded>(awaitInternalState())
            assertEquals(1, loaded.materials.size)
            assertTrue(loaded.materials.none { it.id == "m0" })
        }
    }

    @Test
    fun `Refresh re-fetches and still reports every material`() = runTest {
        val viewModel = MaterialsListViewModel(repository(materialCount = 2))
        viewModel.testWithInternalState(this, MaterialsListState.Loading) {
            runOnCreate()
            assertIs<MaterialsListState.Loaded>(awaitInternalState())

            containerHost.refresh()
            assertIs<MaterialsListState.Loading>(awaitInternalState())
            val loaded = assertIs<MaterialsListState.Loaded>(awaitInternalState())
            assertEquals(setOf("m0", "m1"), loaded.materials.map { it.id }.toSet())
        }
    }
}
