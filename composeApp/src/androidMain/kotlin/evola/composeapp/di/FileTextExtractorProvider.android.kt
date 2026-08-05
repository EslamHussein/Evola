package evola.composeapp.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import evola.shared.files.FileTextExtractor
import evola.shared.files.MIME_DOCX
import evola.shared.files.MIME_PDF
import evola.shared.files.MIME_TEXT_PLAIN
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class AndroidFileTextExtractor(context: Context) : FileTextExtractor {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override fun extractText(bytes: ByteArray, mimeType: String): String? = when (mimeType) {
        MIME_PDF -> runCatching {
            PDDocument.load(bytes).use { PDFTextStripper().getText(it) }
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
