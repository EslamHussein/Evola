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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Same convention as [evola.composeapp.main.HomeContainerTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase]. `init { refresh() }` is a direct (non-launched)
 * suspend call, so the first `Loaded`/`Error` state is already settled by the time the test body
 * runs - only the follow-up `Refresh`/`Delete` intents need `states.first { <specific predicate> }`
 * (see [MaterialDetailContainerTest]'s doc comment for why `wait()` isn't reliable here - both
 * Containers share the same `manageJobs()`-based poll-restart shape). */
class MaterialsListContainerTest {

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
        MaterialsListContainer(repository(materialCount = 3)).store.subscribeAndTest {
            val loaded = assertIs<MaterialsListState.Loaded>(states.value)
            assertEquals(3, loaded.materials.size)
        }
    }

    @Test
    fun `an empty goal loads as an empty list, not a crash`() = runTest {
        MaterialsListContainer(repository(materialCount = 0)).store.subscribeAndTest {
            val loaded = assertIs<MaterialsListState.Loaded>(states.value)
            assertTrue(loaded.materials.isEmpty())
        }
    }

    @Test
    fun `Delete removes exactly the targeted material and reloads the rest`() = runTest {
        MaterialsListContainer(repository(materialCount = 2)).store.subscribeAndTest {
            assertIs<MaterialsListState.Loaded>(states.value)
            intent(MaterialsListIntent.Delete("m0"))
            val loaded = assertIs<MaterialsListState.Loaded>(
                states.first { it is MaterialsListState.Loaded && it.materials.size == 1 },
            )
            assertTrue(loaded.materials.none { it.id == "m0" })
        }
    }

    @Test
    fun `Refresh re-fetches and still reports every material`() = runTest {
        MaterialsListContainer(repository(materialCount = 2)).store.subscribeAndTest {
            assertIs<MaterialsListState.Loaded>(states.value)
            intent(MaterialsListIntent.Refresh)
            val loaded = assertIs<MaterialsListState.Loaded>(
                states.first { it is MaterialsListState.Loaded && it.materials.size == 2 },
            )
            assertEquals(setOf("m0", "m1"), loaded.materials.map { it.id }.toSet())
        }
    }
}
