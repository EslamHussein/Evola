package evola.composeapp.feature.materials.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.composeapp.feature.materials.vm.StagedResource
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.core.network.AnthropicClient
import evola.shared.db.EvolaDatabase
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
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Same convention as [evola.composeapp.feature.home.vm.HomeViewModelTest]: a real [LocalMaterialsRepository]
 * backed by an in-memory SQLite [EvolaDatabase] and a [MockEngine] that always returns an empty
 * Anthropic response, driven through [AiWizardViewModel] via the official
 * `org.orbit-mvi:orbit-test` DSL. `startAnalysis` only needs to be asserted on its immediate,
 * synchronous outcome (`UploadResult`) - the background segmentation/extraction job it kicks off
 * runs on a real (non-test-controlled) [CoroutineScope] and is out of scope here, same as the
 * production [evola.composeapp.di.KoinModules] wiring. */
class AiWizardViewModelTest {

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
    fun `selectResourceType and selectOrganizationMode update state directly`() = runTest {
        val viewModel = AiWizardViewModel("g1", StagedResource.Text("irrelevant"), repository())
        viewModel.testWithInternalState(this, WizardState(stagedTitle = "Pasted text")) {
            containerHost.selectResourceType(ResourceInfoType.WORKBOOK)
            assertEquals(ResourceInfoType.WORKBOOK, awaitInternalState().resourceType)

            containerHost.selectOrganizationMode(OrganizationMode.PAGES)
            assertEquals(OrganizationMode.PAGES, awaitInternalState().organizationMode)

            // MANUAL has no backend support yet - selecting it is a documented no-op, so it never
            // reduces; nothing new to await here.
            containerHost.selectOrganizationMode(OrganizationMode.MANUAL)
        }
    }

    @Test
    fun `goNext and goBack move through the 4 steps and clamp at both ends`() = runTest {
        val viewModel = AiWizardViewModel("g1", StagedResource.Text("irrelevant"), repository())
        viewModel.testWithInternalState(this, WizardState(stagedTitle = "Pasted text")) {
            // Already at the first step - goBack is a documented no-op, so it never reduces.
            containerHost.goBack()

            containerHost.goNext()
            assertEquals(WizardStep.ORGANIZATION, awaitInternalState().step)
            containerHost.goNext()
            assertEquals(WizardStep.FOCUS, awaitInternalState().step)
            containerHost.goNext()
            assertEquals(WizardStep.INSTRUCTIONS, awaitInternalState().step)

            // Already at the last step - clamps, so it never reduces.
            containerHost.goNext()

            containerHost.goBack()
            assertEquals(WizardStep.FOCUS, awaitInternalState().step)
        }
    }

    @Test
    fun `startAnalysis with real text creates a material and posts a MaterialCreated side effect`() = runTest {
        withGoal { repository, goalId ->
            val viewModel = AiWizardViewModel(goalId, StagedResource.Text("A".repeat(50)), repository)
            viewModel.testWithInternalState(this, WizardState(stagedTitle = "Pasted text")) {
                containerHost.startAnalysis()
                assertEquals(WizardSubmitState.Submitting, awaitInternalState().submitState)
                assertEquals(WizardSubmitState.Idle, awaitInternalState().submitState)
                val effect = assertIs<WizardSideEffect.MaterialCreated>(awaitSideEffect())
                assertNotNull(effect.materialId)
            }
        }
    }

    @Test
    fun `startAnalysis with too-little text surfaces NoExtractableText as a safe error message`() = runTest {
        withGoal { repository, goalId ->
            val viewModel = AiWizardViewModel(goalId, StagedResource.Text("short"), repository)
            viewModel.testWithInternalState(this, WizardState(stagedTitle = "Pasted text")) {
                containerHost.startAnalysis()
                assertEquals(WizardSubmitState.Submitting, awaitInternalState().submitState)
                val error = assertIs<WizardSubmitState.Error>(awaitInternalState().submitState)
                assertEquals("There isn't enough text here yet - add more content.", error.message)
            }
        }
    }
}
