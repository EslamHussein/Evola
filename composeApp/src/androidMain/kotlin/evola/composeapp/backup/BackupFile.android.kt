package evola.composeapp.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.OutputStreamWriter

@Composable
actual fun rememberBackupFileSaver(content: () -> String, onSaved: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val contentState = rememberUpdatedState(content)
    val onSavedState = rememberUpdatedState(onSaved)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            onSavedState.value(false)
            return@rememberLauncherForActivityResult
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream).use { it.write(contentState.value()) }
        }
        onSavedState.value(true)
    }
    return { launcher.launch("evola-backup.json") }
}

@Composable
actual fun rememberBackupFileLoader(onLoaded: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val onLoadedState = rememberUpdatedState(onLoaded)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@rememberLauncherForActivityResult
        onLoadedState.value(text)
    }
    return { launcher.launch(arrayOf("application/json")) }
}
