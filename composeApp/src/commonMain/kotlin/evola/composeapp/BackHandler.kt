package evola.composeapp

import androidx.compose.runtime.Composable

/** Intercepts the platform "back" gesture/button so nested screens can navigate up instead of
 * falling through to the OS default (which, with no back-stack navigation library in this app,
 * would otherwise just close the Activity). Android wires this to the real hardware/gesture back
 * event; iOS has no equivalent system event, so its actual is a no-op - back navigation there is
 * already handled by each screen's own on-screen back button. */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
