package evola.composeapp.feature.vocabulary.vm

import evola.shared.feature.vocabulary.data.LocalVocabularyRepository
import evola.shared.feature.vocabulary.domain.VocabularyRepository
import evola.shared.feature.profile.domain.SettingsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/vocabulary`'s own Koin bindings, split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - the [VocabularyRepository] singleton plus every
 * vocabulary-feature ViewModel. [evola.shared.feature.vocabulary.domain.GermanNounLexicon] and its
 * one-time import coordinator stay registered in the root module for now since
 * `evola.shared.feature.materials.domain.MaterialsRepository` (not yet moved to a feature package) also depends on
 * the lexicon - hoisting those two into this module is deferred to whichever phase moves materials.
 */
val vocabularyModule = module {
    single<VocabularyRepository> { LocalVocabularyRepository(get(), get(), get<SettingsRepository>()) }

    viewModel { (lessonId: String) -> BrowseFlashcardsViewModel(lessonId, get()) }
    viewModel { (lessonId: String, goalId: String) -> VocabularyListViewModel(lessonId, goalId, get()) }
    viewModel { (source: VocabSessionSource) -> VocabularySessionViewModel(source, get(), get()) }
}
