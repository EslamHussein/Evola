package evola.composeapp.core.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op: iOS has no hardware/gesture "back" event equivalent to Android's system back.
}
