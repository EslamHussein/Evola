package evola.composeapp.feature.vocabulary.ui

import androidx.compose.runtime.Composable

/** Returns a launcher that opens the system file picker restricted to CSV/plain-text, reading the
 * picked file's full text - Reword's "Import words" feature. [onLoaded] fires once with the file's
 * contents (parsed elsewhere via [evola.shared.feature.vocabulary.domain.parseWordCsv]); never fires on cancel. */
@Composable
expect fun rememberCsvFilePicker(onLoaded: (String) -> Unit): () -> Unit
