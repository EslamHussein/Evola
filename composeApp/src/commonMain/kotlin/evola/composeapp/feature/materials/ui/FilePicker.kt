package evola.composeapp.feature.materials.ui

import androidx.compose.runtime.Composable

data class PickedFile(val fileName: String, val mimeType: String, val bytes: ByteArray)

/** Returns a launcher function - call it to open the system file picker restricted to PDF/DOCX
 * per 01_PRODUCT_SPEC.md §1.5. [onPicked] fires once with the file's bytes already read. */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** Returns a launcher for the system photo picker (gallery) - one image per launch, matching the
 * Add Resource screen's "+" tile (tap it again to add more photos to the grid). [onPicked] fires
 * once per selection with the image's bytes already read. */
@Composable
expect fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit

/** Returns a launcher for the device camera - the "+" tile's other source option, for
 * photographing a page directly instead of picking an existing photo. No-ops (fires nothing) if
 * the device/simulator has no camera. Same [onPicked] contract as [rememberImagePicker]. */
@Composable
expect fun rememberCameraCapture(onPicked: (PickedFile) -> Unit): () -> Unit
