package evola.server

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

const val MIME_PDF = "application/pdf"
const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

private const val PDF_MAGIC = "%PDF-"

sealed interface FileParseOutcome {
    data class Success(val text: String, val pageCount: Int?) : FileParseOutcome
    data object PasswordProtected : FileParseOutcome
    data object Corrupted : FileParseOutcome
}

/**
 * PDF/DOCX text extraction per 01_PRODUCT_SPEC.md §1.5 - MIME type is sniffed from file content
 * (magic bytes), never trusted from the client's declared content-type or file extension.
 */
object FileTextExtractor {

    /** Returns the real MIME type from file content, or null if it's neither PDF nor DOCX. */
    fun detectMimeType(bytes: ByteArray): String? = when {
        looksLikePdf(bytes) -> MIME_PDF
        looksLikeDocx(bytes) -> MIME_DOCX
        else -> null
    }

    fun extractText(bytes: ByteArray, mimeType: String): FileParseOutcome = when (mimeType) {
        MIME_PDF -> extractPdf(bytes)
        MIME_DOCX -> extractDocx(bytes)
        else -> FileParseOutcome.Corrupted
    }

    private fun looksLikePdf(bytes: ByteArray): Boolean =
        bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == PDF_MAGIC

    /** DOCX is a zip archive; a plain "starts with PK" check would also match plain zip files, so
     * this confirms the archive actually contains the OOXML word-document entry. */
    private fun looksLikeDocx(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return false
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.any { it.name == "word/document.xml" }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractPdf(bytes: ByteArray): FileParseOutcome = try {
        Loader.loadPDF(bytes).use { document ->
            FileParseOutcome.Success(PDFTextStripper().getText(document), document.numberOfPages)
        }
    } catch (e: InvalidPasswordException) {
        FileParseOutcome.PasswordProtected
    } catch (e: Exception) {
        FileParseOutcome.Corrupted
    }

    private fun extractDocx(bytes: ByteArray): FileParseOutcome = try {
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val text = XWPFWordExtractor(document).use { it.text }
            FileParseOutcome.Success(text, null)
        }
    } catch (e: Exception) {
        FileParseOutcome.Corrupted
    }
}
