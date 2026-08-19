package evola.composeapp.vocabulary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

@Composable
actual fun rememberCsvFilePicker(onLoaded: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val onLoadedState = rememberUpdatedState(onLoaded)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        onLoadedState.value(text)
    }
    return { launcher.launch(CSV_MIME_TYPES) }
}
