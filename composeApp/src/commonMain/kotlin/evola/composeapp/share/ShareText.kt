package evola.composeapp.share

import androidx.compose.runtime.Composable

/** Reword's "Share progress" - the platform text share sheet (Android `ACTION_SEND`, iOS
 * `UIActivityViewController`), not a file - a human-readable summary, distinct from
 * [evola.composeapp.backup.rememberBackupFileSaver]'s JSON snapshot. */
@Composable
expect fun rememberShareText(): (String) -> Unit
