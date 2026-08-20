package evola.composeapp.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.MIME_DOCX
import evola.shared.core.common.MIME_PDF
import evola.shared.core.common.MIME_TEXT_PLAIN
import evola.shared.core.common.PAGE_BREAK
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class AndroidFileTextExtractor(context: Context) : FileTextExtractor {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override fun extractText(bytes: ByteArray, mimeType: String): String? = when (mimeType) {
        // Per-page (not one whole-document strip) so the "pages" organization mode can split back
        // into pages via PAGE_BREAK - see PageSegmenter.
        MIME_PDF -> runCatching {
            PDDocument.load(bytes).use { doc ->
                (1..doc.numberOfPages).joinToString(PAGE_BREAK) { pageNum ->
                    PDFTextStripper().apply { startPage = pageNum; endPage = pageNum }.getText(doc)
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        MIME_DOCX -> runCatching { extractDocx(bytes) }.getOrNull()?.takeIf { it.isNotBlank() }
        MIME_TEXT_PLAIN -> bytes.decodeToString().takeIf { it.isNotBlank() }
        else -> null
    }

    /** DOCX is a ZIP; the text lives in word/document.xml. Best-effort: paragraph breaks → newlines,
     * strip tags, unescape the handful of XML entities. No Apache POI (heavy on Android). */
    private fun extractDocx(bytes: ByteArray): String {
        val xml = ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            generateSequence { zis.nextEntry }
                .firstOrNull { it.name == "word/document.xml" }
                ?.let { zis.readBytes().decodeToString() }
        } ?: return ""
        return xml
            .replace("</w:p>", "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .trim()
    }
}

@Composable
actual fun rememberFileTextExtractor(): FileTextExtractor {
    val context = LocalContext.current
    return remember { AndroidFileTextExtractor(context.applicationContext) }
}
