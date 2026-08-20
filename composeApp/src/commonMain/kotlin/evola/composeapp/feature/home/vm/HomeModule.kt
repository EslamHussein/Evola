package evola.composeapp.feature.home.vm

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/home`'s own Koin bindings, split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - [HomeViewModel] plus [ProcessingStatusViewModel],
 * following the exact pattern [evola.composeapp.feature.vocabulary.vm.vocabularyModule] established.
 * [ProcessingStatusViewModel] lives here (not in `feature/materials`) since it's rendered on Home
 * even though it depends on `feature/materials/domain`'s `MaterialsRepository` - allowed cross-feature
 * `vm -> domain` dependency, per the plan's core-buckets decision.
 */
val homeModule = module {
    viewModel { (goalId: String) -> HomeViewModel(goalId, get()) }
    viewModel { ProcessingStatusViewModel(get()) }
}
