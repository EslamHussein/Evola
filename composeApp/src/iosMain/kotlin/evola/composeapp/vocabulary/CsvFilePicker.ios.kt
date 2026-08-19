package evola.composeapp.vocabulary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private class CsvPickerDelegate(private val onPicked: (String) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val accessed = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return
            onPicked(data.toUtf8String())
        } finally {
            if (accessed) url.stopAccessingSecurityScopedResource()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return out.decodeToString()
}

@Composable
actual fun rememberCsvFilePicker(onLoaded: (String) -> Unit): () -> Unit {
    val onLoadedState = rememberUpdatedState(onLoaded)
    val delegate = remember { CsvPickerDelegate { onLoadedState.value(it) } }
    return {
        val types = listOfNotNull(UTTypeCommaSeparatedText, UTTypePlainText, UTType.typeWithFilenameExtension("csv"))
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}
