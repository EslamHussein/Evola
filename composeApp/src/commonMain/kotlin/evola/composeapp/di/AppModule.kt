package evola.composeapp.di

import evola.composeapp.KEY_ANTHROPIC_API_KEY
import evola.composeapp.SecureStore
import evola.shared.ai.AnthropicClient
import evola.shared.ai.GrammarExtractor
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.db.EvolaDatabase
import evola.shared.files.FileTextExtractor
import evola.shared.local.AiVocabularyFreeProductionGrader
import evola.shared.local.LocalGoalsRepository
import evola.shared.local.LocalGrammarRepository
import evola.shared.local.LocalLessonsRepository
import evola.shared.local.LocalMaterialsRepository
import evola.shared.local.LocalVocabularyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single composition root for the serverless, single-user app (manual DI — no Koin). Builds the
 * on-device SQLDelight database, one [AnthropicClient] pointed at the user's own locally-stored key,
 * the three extraction pipelines, and the five [Local*Repository][LocalGoalsRepository]
 * implementations behind the identical interfaces the ViewModels already use — so nothing above the
 * DI layer changed when the Ktor/Evola-backend repositories were swapped out. The only network
 * dependency left is Anthropic itself (Claude can't run on-device).
 */
class AppModule(
    driverFactory: DatabaseDriverFactory,
    secureStore: SecureStore,
    fileTextExtractor: FileTextExtractor,
) {
    private val database = EvolaDatabase(driverFactory.create())
    private val engine = platformHttpEngine()
    private val anthropic = AnthropicClient(engine) { secureStore.get(KEY_ANTHROPIC_API_KEY) }

    // Extraction runs here as a coroutine (replacing the server's job-queue workers); a supervisor
    // scope so one material's failure never cancels another's.
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val goalsRepository = LocalGoalsRepository(database)
    val lessonsRepository = LocalLessonsRepository(database)
    val vocabularyRepository = LocalVocabularyRepository(database, AiVocabularyFreeProductionGrader(anthropic))
    val grammarRepository = LocalGrammarRepository(database)
    val materialsRepository = LocalMaterialsRepository(
        db = database,
        fileTextExtractor = fileTextExtractor,
        segmentation = SegmentationExtractor(anthropic),
        vocabExtractor = VocabularyExtractor(anthropic),
        grammarExtractor = GrammarExtractor(anthropic),
        scope = extractionScope,
    )
}
