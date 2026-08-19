package evola.composeapp.backup

import androidx.compose.runtime.Composable

/** Returns a launcher that opens the system "save file" flow, writing [content]'s result (computed
 * lazily at save-time, not on every recomposition) as a `.json` file the user picks a location for.
 * [onSaved] fires once with whether a location was actually chosen (false on cancel). */
@Composable
expect fun rememberBackupFileSaver(content: () -> String, onSaved: (Boolean) -> Unit): () -> Unit

/** Returns a launcher that opens the system file picker restricted to JSON, reading the picked
 * file's full text. [onLoaded] fires once with the file's contents; never fires if the user cancels. */
@Composable
expect fun rememberBackupFileLoader(onLoaded: (String) -> Unit): () -> Unit
