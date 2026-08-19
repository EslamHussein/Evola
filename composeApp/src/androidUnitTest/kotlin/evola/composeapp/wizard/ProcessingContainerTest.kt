package evola.composeapp.wizard

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeContainerTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase]. Seeds a material already in a terminal status
 * (READY/FAILED) so the very first poll tick resolves without needing to manipulate virtual time -
 * [ProcessingContainer] uses `asyncInit`, so state stays [ProcessingState.Loading] until the first
 * real fetch lands. */
class ProcessingContainerTest {

    private fun repository(materialId: String, status: String): MaterialsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
        db.materialsQueries.insert(materialId, LOCAL_USER, "g1", "f.pdf", "h", status, "application/pdf", 1L, null, "auto", null, null, "txt", 0L)
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
        ProcessingContainer("m1", repository("m1", "READY")).store.subscribeAndTest {
            val final = states.first { it !is ProcessingState.Loading }
            val done = assertIs<ProcessingState.Done>(final)
            assertEquals("m1", done.materialId)
        }
    }

    @Test
    fun `a material already FAILED resolves to Done too - Resource Details renders the failure, not this screen`() = runTest {
        ProcessingContainer("m1", repository("m1", "FAILED")).store.subscribeAndTest {
            val final = states.first { it !is ProcessingState.Loading }
            assertIs<ProcessingState.Done>(final)
        }
    }

    @Test
    fun `an unknown material id surfaces as Error, not a crash`() = runTest {
        ProcessingContainer("does-not-exist", repository("m1", "READY")).store.subscribeAndTest {
            val final = states.first { it !is ProcessingState.Loading }
            val error = assertIs<ProcessingState.Error>(final)
            assertTrue(error.message.isNotBlank())
        }
    }
}
