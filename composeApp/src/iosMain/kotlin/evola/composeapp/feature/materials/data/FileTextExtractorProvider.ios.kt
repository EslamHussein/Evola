package evola.composeapp.feature.materials.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.MIME_PDF
import evola.shared.core.common.MIME_TEXT_PLAIN
import evola.shared.core.common.PAGE_BREAK
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

class IosFileTextExtractor : FileTextExtractor {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun extractText(bytes: ByteArray, mimeType: String): String? = when (mimeType) {
        // Per-page (not the whole-document .string) so the "pages" organization mode can split
        // back into pages via PAGE_BREAK - see PageSegmenter.
        MIME_PDF -> {
            if (bytes.isEmpty()) null
            else {
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                PDFDocument(data = data).let { doc ->
                    (0uL until doc.pageCount).mapNotNull { i -> doc.pageAtIndex(i)?.string() }.joinToString(PAGE_BREAK)
                }.takeIf { it.isNotBlank() }
            }
        }
        MIME_TEXT_PLAIN -> bytes.decodeToString().takeIf { it.isNotBlank() }
        // DOCX on iOS is deferred (no built-in ZIP) — falls through to the UNSUPPORTED path.
        else -> null
    }
}

@Composable
actual fun rememberFileTextExtractor(): FileTextExtractor = remember { IosFileTextExtractor() }
