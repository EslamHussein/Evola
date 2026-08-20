package evola.composeapp.feature.learning.vm

import evola.shared.feature.learning.data.LocalGrammarRepository
import evola.shared.feature.learning.data.LocalLessonsRepository
import evola.shared.feature.learning.domain.GrammarRepository
import evola.shared.feature.learning.domain.LessonsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/learning`'s own Koin bindings, split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - the [LessonsRepository]/[GrammarRepository] singletons
 * plus every learning-feature ViewModel (lesson detail, grammar topic list, grammar exercise
 * session), following the exact pattern [evola.composeapp.feature.vocabulary.vm.vocabularyModule]
 * established.
 */
val learningModule = module {
    single<LessonsRepository> { LocalLessonsRepository(get()) }
    single<GrammarRepository> { LocalGrammarRepository(get()) }

    viewModel { (topicId: String) -> GrammarExerciseSessionViewModel(topicId, get()) }
    viewModel { (lessonId: String) -> GrammarTopicListViewModel(lessonId, get()) }
    viewModel { (lessonId: String) -> LessonDetailViewModel(lessonId, get()) }
}
