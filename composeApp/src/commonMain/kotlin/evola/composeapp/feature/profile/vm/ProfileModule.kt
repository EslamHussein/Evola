package evola.composeapp.feature.profile.vm

import evola.shared.feature.profile.data.LocalAchievementsRepository
import evola.shared.feature.profile.data.LocalBackupRepository
import evola.shared.feature.profile.data.LocalSettingsRepository
import evola.shared.feature.profile.domain.AchievementsRepository
import evola.shared.feature.profile.domain.BackupRepository
import evola.shared.feature.profile.domain.SettingsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `feature/profile`'s own Koin bindings, split out of the root `evolaModule` (see
 * [evola.composeapp.core.di.evolaModule]) - the [SettingsRepository], [BackupRepository], and
 * [AchievementsRepository] singletons plus [ProfileViewModel]/[SettingsViewModel], following the
 * exact pattern [evola.composeapp.feature.vocabulary.vm.vocabularyModule] established. Note
 * [AchievementsRepository] and [SettingsRepository] are also read by `feature/onboarding`'s
 * `GoalsRepository` via Koin `get()` - expected cross-feature `domain -> domain` dependency, see
 * [evola.composeapp.feature.onboarding.vm.onboardingModule]'s doc comment.
 */
val profileModule = module {
    single<SettingsRepository> { LocalSettingsRepository(get()) }
    single<BackupRepository> { LocalBackupRepository(get()) }
    single<AchievementsRepository> { LocalAchievementsRepository(get()) }

    viewModel { (goalId: String) -> ProfileViewModel(goalId, get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
