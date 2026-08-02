package evola.composeapp.materials

import androidx.compose.runtime.Composable

data class PickedFile(val fileName: String, val mimeType: String, val bytes: ByteArray)

/** Returns a launcher function - call it to open the system file picker restricted to PDF/DOCX
 * per 01_PRODUCT_SPEC.md §1.5. [onPicked] fires once with the file's bytes already read. */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit
