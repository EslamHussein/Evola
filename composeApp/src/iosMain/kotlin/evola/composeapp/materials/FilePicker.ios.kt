package evola.composeapp.materials

import androidx.compose.runtime.Composable

/**
 * iOS file picker deferred until iOS verification resumes (see docs/ROADMAP.md) - same
 * placeholder-not-fake precedent as SessionStorage.ios.kt: enough to keep the multiplatform
 * build working, not yet a real UIDocumentPickerViewController implementation.
 */
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit = {}
