package evola.composeapp.main

import evola.shared.local.LocalSettingsRepository
import pro.respawn.flowmvi.android.StoreViewModel

class SettingsViewModel(repository: LocalSettingsRepository) :
    StoreViewModel<SettingsState, SettingsIntent, Nothing>(SettingsContainer(repository))
