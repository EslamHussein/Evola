package evola.composeapp.widget

import androidx.compose.runtime.Composable

/** Nudges the home-screen widget (Android only - see [evola.composeapp.widget.HomeWidgetProvider]'s
 * own doc comment for why iOS has no equivalent) to refresh immediately rather than waiting for its
 * next scheduled tick, after anything that changes the streak/due count. */
@Composable
expect fun rememberWidgetRefresher(): () -> Unit
