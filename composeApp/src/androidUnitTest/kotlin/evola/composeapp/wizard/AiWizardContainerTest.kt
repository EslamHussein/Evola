package evola.composeapp.wizard

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.composeapp.materials.StagedResource
import evola.shared.ai.GrammarExtractor
import evola.shared.ai.ImageTranscriber
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.ai.AnthropicClient
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
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Same convention as [evola.composeapp.main.HomeContainerTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase] and a [MockEngine] that always returns an empty
 * Anthropic response, driven through [AiWizardContainer.store] via the official
 * `pro.respawn.flowmvi:test` DSL. `StartAnalysis` only needs to be asserted on its immediate,
 * synchronous outcome (`UploadResult`) - the background segmentation/extraction job it kicks off
 * runs on a real (non-test-controlled) [CoroutineScope] and is out of scope here, same as the
 * production [evola.composeapp.di.KoinModules] wiring. */
class AiWizardContainerTest {

    private fun repository(): MaterialsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
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

    private suspend fun withGoal(block: suspend (MaterialsRepository, goalId: String) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        db.goalsQueries.insert("g1", LOCAL_USER, "Learn German", "t", "en", 1L, 0L, 0L)
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
        block(repository, "g1")
    }

    @Test
    fun `SelectResourceType and SelectOrganizationMode update state directly`() = runTest {
        AiWizardContainer("g1", StagedResource.Text("irrelevant"), repository()).store.subscribeAndTest {
            WizardIntent.SelectResourceType(ResourceInfoType.WORKBOOK) resultsIn {
                wait()
                assertEquals(ResourceInfoType.WORKBOOK, states.value.resourceType)
            }
            WizardIntent.SelectOrganizationMode(OrganizationMode.PAGES) resultsIn {
                wait()
                assertEquals(OrganizationMode.PAGES, states.value.organizationMode)
            }
            // MANUAL has no backend support yet - selecting it is a documented no-op.
            WizardIntent.SelectOrganizationMode(OrganizationMode.MANUAL) resultsIn {
                wait()
                assertEquals(OrganizationMode.PAGES, states.value.organizationMode)
            }
        }
    }

    @Test
    fun `GoNext and GoBack move through the 4 steps and clamp at both ends`() = runTest {
        AiWizardContainer("g1", StagedResource.Text("irrelevant"), repository()).store.subscribeAndTest {
            assertEquals(WizardStep.RESOURCE_INFO, states.value.step)
            WizardIntent.GoBack resultsIn { wait(); assertEquals(WizardStep.RESOURCE_INFO, states.value.step) }
            WizardIntent.GoNext resultsIn { wait(); assertEquals(WizardStep.ORGANIZATION, states.value.step) }
            WizardIntent.GoNext resultsIn { wait(); assertEquals(WizardStep.FOCUS, states.value.step) }
            WizardIntent.GoNext resultsIn { wait(); assertEquals(WizardStep.INSTRUCTIONS, states.value.step) }
            WizardIntent.GoNext resultsIn { wait(); assertEquals(WizardStep.INSTRUCTIONS, states.value.step) }
            WizardIntent.GoBack resultsIn { wait(); assertEquals(WizardStep.FOCUS, states.value.step) }
        }
    }

    @Test
    fun `StartAnalysis with real text creates a material and reports MaterialCreatedEvent`() = runTest {
        withGoal { repository, goalId ->
            AiWizardContainer(goalId, StagedResource.Text("A".repeat(50)), repository).store.subscribeAndTest {
                WizardIntent.StartAnalysis resultsIn {
                    wait()
                    val event = assertNotNull(states.value.materialCreated)
                    assertEquals(WizardSubmitState.Idle, states.value.submitState)
                    assertNotNull(event.materialId)
                }
            }
        }
    }

    @Test
    fun `StartAnalysis with too-little text surfaces NoExtractableText as a safe error message`() = runTest {
        withGoal { repository, goalId ->
            AiWizardContainer(goalId, StagedResource.Text("short"), repository).store.subscribeAndTest {
                WizardIntent.StartAnalysis resultsIn {
                    wait()
                    val error = assertIs<WizardSubmitState.Error>(states.value.submitState)
                    assertEquals("There isn't enough text here yet - add more content.", error.message)
                    assertNull(states.value.materialCreated)
                }
            }
        }
    }
}
