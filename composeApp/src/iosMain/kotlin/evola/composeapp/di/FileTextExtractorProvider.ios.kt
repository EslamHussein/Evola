package evola.composeapp.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import evola.shared.files.FileTextExtractor
import evola.shared.files.MIME_PDF
import evola.shared.files.MIME_TEXT_PLAIN
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

class IosFileTextExtractor : FileTextExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override fun extractText(bytes: ByteArray, mimeType: String): String? = when (mimeType) {
        MIME_PDF -> {
            if (bytes.isEmpty()) null
            else {
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                PDFDocument(data = data)?.string()?.takeIf { it.isNotBlank() }
            }
        }
        MIME_TEXT_PLAIN -> bytes.decodeToString().takeIf { it.isNotBlank() }
        // DOCX on iOS is deferred (no built-in ZIP) — falls through to the UNSUPPORTED path.
        else -> null
    }
}

@Composable
actual fun rememberFileTextExtractor(): FileTextExtractor = remember { IosFileTextExtractor() }
