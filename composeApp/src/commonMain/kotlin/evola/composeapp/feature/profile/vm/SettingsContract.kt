package evola.composeapp.feature.profile.vm

import evola.shared.feature.profile.domain.AppSettings

data class SettingsState(val settings: AppSettings = AppSettings())
