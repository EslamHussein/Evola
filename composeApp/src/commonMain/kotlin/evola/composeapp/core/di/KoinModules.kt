package evola.composeapp.core.di

import evola.composeapp.KEY_ANTHROPIC_API_KEY
import evola.composeapp.SecureStore
import evola.composeapp.core.database.DatabaseDriverFactory
import evola.composeapp.core.network.platformHttpEngine
import evola.composeapp.generated.resources.Res
import evola.shared.core.network.AnthropicClient
import evola.shared.ai.GrammarExtractor
import evola.shared.ai.ImageTranscriber
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.db.EvolaDatabase
import evola.shared.core.common.FileTextExtractor
import evola.shared.achievements.AchievementsRepository
import evola.shared.goals.GoalsRepository
import evola.shared.local.BackupRepository
import evola.shared.local.LocalAchievementsRepository
import evola.shared.local.LocalBackupRepository
import evola.shared.local.LocalGoalsRepository
import evola.shared.local.LocalMaterialsRepository
import evola.shared.local.LocalSettingsRepository
import evola.shared.local.SettingsRepository
import evola.composeapp.feature.learning.vm.learningModule
import evola.composeapp.feature.vocabulary.vm.vocabularyModule
import evola.composeapp.main.HomeViewModel
import evola.composeapp.main.ProcessingStatusViewModel
import evola.composeapp.main.ProfileViewModel
import evola.composeapp.main.SettingsViewModel
import evola.composeapp.materials.AddMaterialViewModel
import evola.composeapp.materials.MaterialDetailViewModel
import evola.composeapp.materials.MaterialsListViewModel
import evola.composeapp.materials.StagedResource
import evola.composeapp.onboarding.GoalSetupViewModel
import evola.composeapp.wizard.AiWizardViewModel
import evola.composeapp.wizard.ProcessingViewModel
import evola.shared.materials.MaterialsRepository
import evola.shared.feature.vocabulary.domain.GermanNounImportState
import evola.shared.feature.vocabulary.domain.GermanNounImporter
import evola.shared.feature.vocabulary.domain.GermanNounLexicon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * One-time import of the bundled ~20MB German noun dataset into the local `german_nouns` table -
 * a no-op on every launch after the first, since [GermanNounImporter] checks the row count first.
 * A dedicated class (rather than inline module logic) so its `init` block - which must run exactly
 * once, eagerly, not on first lookup - has somewhere to live; registered `createdAtStart = true`.
 */
class GermanNounImportCoordinator(database: EvolaDatabase, extractionScope: CoroutineScope) {
    private val _state = MutableStateFlow<GermanNounImportState>(GermanNounImportState.NotStarted)
    val state: StateFlow<GermanNounImportState> = _state.asStateFlow()

    init {
        extractionScope.launch {
            GermanNounImporter(database).importIfNeeded(Res.readBytes("files/german_nouns.csv").decodeToString()) { imported, total ->
                _state.value = GermanNounImportState.InProgress(imported, total)
            }
            _state.value = GermanNounImportState.Done
        }
    }
}

/**
 * Replaces the old [AppModule]-class composition root with a Koin module: same singletons (the
 * on-device SQLDelight database, one [AnthropicClient] pointed at the user's own locally-stored
 * key, the three extraction pipelines, and the seven Local*Repository implementations), just
 * resolved via `get()` instead of threaded as composable params through every screen. The only
 * network dependency left is Anthropic itself (Claude can't run on-device).
 */
fun evolaModule(
    driverFactory: DatabaseDriverFactory,
    secureStore: SecureStore,
    fileTextExtractor: FileTextExtractor,
): Module = module {
    includes(vocabularyModule, learningModule)

    single { EvolaDatabase(driverFactory.create()) }
    single { platformHttpEngine() }
    single { AnthropicClient(get()) { secureStore.get(KEY_ANTHROPIC_API_KEY) } }

    // Supervisor scope so one material's extraction failure never cancels another's - replaces
    // the server's job-queue workers.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { GermanNounLexicon(get()) }
    single(createdAtStart = true) { GermanNounImportCoordinator(get(), get()) }

    single<SettingsRepository> { LocalSettingsRepository(get()) }
    single<BackupRepository> { LocalBackupRepository(get()) }
    single<AchievementsRepository> { LocalAchievementsRepository(get()) }
    single<GoalsRepository> { LocalGoalsRepository(get(), get<SettingsRepository>(), get<AchievementsRepository>()) }
    single<MaterialsRepository> {
        LocalMaterialsRepository(
            db = get(),
            fileTextExtractor = fileTextExtractor,
            segmentation = SegmentationExtractor(get()),
            vocabExtractor = VocabularyExtractor(get(), nounLexicon = get()),
            grammarExtractor = GrammarExtractor(get()),
            imageTranscriber = ImageTranscriber(get()),
            scope = get(),
        )
    }

    viewModel { AddMaterialViewModel() }
    viewModel { (goalId: String) -> HomeViewModel(goalId, get()) }
    viewModel { ProcessingStatusViewModel(get()) }
    viewModel { (goalId: String) -> ProfileViewModel(goalId, get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { (materialId: String) -> MaterialDetailViewModel(materialId, get()) }
    viewModel { MaterialsListViewModel(get()) }
    viewModel { GoalSetupViewModel(get()) }
    viewModel { (goalId: String, staged: StagedResource) -> AiWizardViewModel(goalId, staged, get()) }
    viewModel { (materialId: String) -> ProcessingViewModel(materialId, get()) }
}
