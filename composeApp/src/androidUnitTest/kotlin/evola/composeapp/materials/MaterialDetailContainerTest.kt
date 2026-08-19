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
import evola.shared.materials.MaterialStatus
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
 * backed by an in-memory SQLite [EvolaDatabase]. [MaterialDetailContainer]'s poll job runs via
 * `manageJobs()`/`launch { }`, not on a dispatcher `TestScope.advanceUntilIdle()` (the `wait()`
 * helper) reliably drains - confirmed by direct experiment: after `Retry` + `wait()`, the DB write
 * `reprocess()` makes hadn't happened yet. `states.first { <specific target predicate> }` (real
 * suspension, not virtual-time draining) is the pattern that actually works here, so every
 * intent-driven assertion below dispatches via plain `intent()` and awaits the real outcome that
 * way instead of `resultsIn { wait(); ... }`. */
class MaterialDetailContainerTest {

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
        MaterialDetailContainer("m1", repository(lessonCount = 2)).store.subscribeAndTest {
            val loaded = assertIs<MaterialDetailState.Loaded>(states.first { it !is MaterialDetailState.Loading })
            assertEquals(2, loaded.detail.lessons.size)
        }
    }

    @Test
    fun `DeleteLesson removes exactly the targeted lesson`() = runTest {
        MaterialDetailContainer("m1", repository(lessonCount = 2)).store.subscribeAndTest {
            states.first { it !is MaterialDetailState.Loading }
            intent(MaterialDetailIntent.DeleteLesson("l0"))
            val loaded = assertIs<MaterialDetailState.Loaded>(
                states.first { it is MaterialDetailState.Loaded && it.detail.lessons.size == 1 },
            )
            assertTrue(loaded.detail.lessons.none { it.id == "l0" })
        }
    }

    @Test
    fun `Retry on a FAILED material re-enters PROCESSING`() = runTest {
        MaterialDetailContainer("m1", repository(status = "FAILED")).store.subscribeAndTest {
            states.first { it !is MaterialDetailState.Loading }
            intent(MaterialDetailIntent.Retry)
            val processing = assertIs<MaterialDetailState.Loaded>(
                states.first { it is MaterialDetailState.Loaded && it.detail.material.status == MaterialStatus.PROCESSING },
            )
            assertEquals(MaterialStatus.PROCESSING, processing.detail.material.status)
        }
    }

    @Test
    fun `an unknown material id surfaces as Error, not a crash`() = runTest {
        MaterialDetailContainer("does-not-exist", repository()).store.subscribeAndTest {
            val error = assertIs<MaterialDetailState.Error>(states.first { it !is MaterialDetailState.Loading })
            assertTrue(error.message.isNotBlank())
        }
    }
}
