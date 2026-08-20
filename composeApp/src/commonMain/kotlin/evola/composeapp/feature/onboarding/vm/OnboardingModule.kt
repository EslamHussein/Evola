package evola.composeapp.feature.onboarding.vm

import evola.shared.feature.profile.domain.AchievementsRepository
import evola.shared.feature.onboarding.data.LocalGoalsRepository
import evola.shared.feature.onboarding.domain.GoalsRepository
import evola.shared.feature.profile.domain.SettingsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/onboarding`'s own Koin bindings, split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - the [GoalsRepository] singleton plus [GoalSetupViewModel],
 * following the exact pattern [evola.composeapp.feature.vocabulary.vm.vocabularyModule] established.
 * [GoalsRepository] is read by several other features (home, profile) and by `App.kt`'s own root
 * routing - that's expected `domain -> domain` cross-feature dependency, not something to avoid (see
 * the plan's core-buckets decision to treat `Goal`/`GoalsRepository` as `feature/onboarding/domain`).
 */
val onboardingModule = module {
    single<GoalsRepository> { LocalGoalsRepository(get(), get<SettingsRepository>(), get<AchievementsRepository>()) }

    viewModel { GoalSetupViewModel(get()) }
}
