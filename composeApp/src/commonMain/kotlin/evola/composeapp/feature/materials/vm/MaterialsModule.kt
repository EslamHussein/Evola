package evola.composeapp.feature.materials.vm

import evola.shared.core.common.FileTextExtractor
import evola.shared.feature.materials.data.LocalMaterialsRepository
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.MaterialsRepository
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.feature.vocabulary.domain.GermanNounLexicon
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/materials`'s own Koin bindings (also covers the AI-wizard/processing post-upload flow,
 * which isn't independently navigable), split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - the [MaterialsRepository] singleton plus every
 * materials/wizard-feature ViewModel. `fileTextExtractor` is threaded through as a parameter (not
 * a Koin singleton itself) since it's supplied by the platform composition root, mirroring how
 * `evolaModule` itself takes it. [GermanNounLexicon] (previously left in the root module per
 * [evola.composeapp.feature.vocabulary.vm.vocabularyModule]'s doc comment - only [MaterialsRepository]'s
 * vocabulary-extraction step actually needs it) now lives here.
 */
fun materialsModule(fileTextExtractor: FileTextExtractor): Module = module {
    single { GermanNounLexicon(get()) }

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
    viewModel { (materialId: String) -> MaterialDetailViewModel(materialId, get()) }
    viewModel { MaterialsListViewModel(get()) }
    viewModel { (goalId: String, staged: StagedResource) -> AiWizardViewModel(goalId, staged, get()) }
    viewModel { (materialId: String) -> ProcessingViewModel(materialId, get()) }
}
