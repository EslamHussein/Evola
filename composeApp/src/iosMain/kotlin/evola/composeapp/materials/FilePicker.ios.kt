package evola.composeapp.materials

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import evola.shared.files.MIME_DOCX
import evola.shared.files.MIME_PDF
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Real iOS file picker (replaces the earlier build-only stub): presents a
 * [UIDocumentPickerViewController] restricted to PDF/DOCX, reads the picked file's bytes through a
 * security-scoped resource, and fires [onPicked] with the same [PickedFile] contract Android uses,
 * so the Add-Resource → wizard → extraction path works identically on both platforms.
 */
@OptIn(ExperimentalForeignApi::class)
private class FilePickerDelegate(
    private val onPicked: (PickedFile) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val accessed = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return
            val fileName = url.lastPathComponent ?: "upload"
            val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
                "pdf" -> MIME_PDF
                "docx" -> MIME_DOCX
                else -> "application/octet-stream"
            }
            onPicked(PickedFile(fileName, mimeType, data.toByteArray()))
        } finally {
            if (accessed) url.stopAccessingSecurityScopedResource()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        // User dismissed the picker — nothing staged, matching Android's null-uri branch.
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return out
}

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val onPickedState = rememberUpdatedState(onPicked)
    // A UIKit delegate is held weakly by the picker, so keep a strong reference across recompositions
    // or it can be collected while the picker is open.
    val delegate = remember { FilePickerDelegate { onPickedState.value(it) } }
    return {
        val types = listOfNotNull(UTTypePDF, UTType.typeWithFilenameExtension("docx"))
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}

/** One photo per launch (tap the Add Resource screen's "+" tile again for more) - mirrors
 * [FilePickerDelegate]'s pattern but for [UIImagePickerController]'s photo-library source. */
@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate(
    private val onPicked: (PickedFile) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        val data = UIImageJPEGRepresentation(image, 0.85) ?: return
        onPicked(PickedFile("photo.jpg", "image/jpeg", data.toByteArray()))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

@Composable
actual fun rememberImagePicker(onPicked: (PickedFile) -> Unit): () -> Unit {
    val onPickedState = rememberUpdatedState(onPicked)
    val delegate = remember { ImagePickerDelegate { onPickedState.value(it) } }
    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberCameraCapture(onPicked: (PickedFile) -> Unit): () -> Unit {
    val onPickedState = rememberUpdatedState(onPicked)
    val delegate = remember { ImagePickerDelegate { onPickedState.value(it) } }
    return {
        // No-ops on simulators/devices without a camera - matches this function's documented
        // contract (rather than crashing UIImagePickerController, which requires this check).
        if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}
