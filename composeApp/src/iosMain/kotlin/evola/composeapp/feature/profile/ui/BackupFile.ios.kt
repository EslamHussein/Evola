package evola.composeapp.feature.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.Foundation.NSUTF8StringEncoding
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import platform.posix.memcpy

/** No native "save to a location I pick" API without a full Files-app integration - iOS's own
 * idiom for this is the share sheet (AirDrop, Save to Files, Mail, ...), so that's what this opens,
 * writing [content] to a temp file first since [UIActivityViewController] shares file URLs, not
 * raw strings held in memory. */
// `as NSString` below is Kotlin/Native's toll-free-bridging idiom - it works at runtime even
// though the compiler can't prove the static relationship, hence the warning.
@Suppress("CAST_NEVER_SUCCEEDS")
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberBackupFileSaver(content: () -> String, onSaved: (Boolean) -> Unit): () -> Unit {
    val contentState = rememberUpdatedState(content)
    val onSavedState = rememberUpdatedState(onSaved)
    return {
        val path = NSTemporaryDirectory() + "evola-backup.json"
        (contentState.value() as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
        val url = NSURL.fileURLWithPath(path)
        val activity = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(activity, animated = true, completion = null)
        onSavedState.value(true)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class BackupPickerDelegate(private val onPicked: (String) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
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
actual fun rememberBackupFileLoader(onLoaded: (String) -> Unit): () -> Unit {
    val onLoadedState = rememberUpdatedState(onLoaded)
    val delegate = remember { BackupPickerDelegate { onLoadedState.value(it) } }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON))
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}
