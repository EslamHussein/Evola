package evola.shared.files

const val MIME_PDF = "application/pdf"
const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
const val MIME_TEXT_PLAIN = "text/plain"

/** Sniffs the MIME type from the file's magic bytes (never trust a client-declared extension) —
 * pure, so it lives in commonMain. PDF starts with "%PDF-"; DOCX is a ZIP ("PK\x03\x04") whose
 * entries include word/. */
fun detectMimeType(bytes: ByteArray): String? = when {
    looksLikePdf(bytes) -> MIME_PDF
    looksLikeZip(bytes) -> MIME_DOCX
    else -> null
}

private fun looksLikePdf(bytes: ByteArray): Boolean =
    bytes.size >= 5 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte() && bytes[4] == '-'.code.toByte()

private fun looksLikeZip(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

/**
 * On-device PDF/DOCX/plain-text extraction (01_PRODUCT_SPEC.md §1.5). Platform implementations live
 * in `:composeApp` (Android: PdfBox-Android + ZIP-based DOCX; iOS: PDFKit) and are injected into the
 * local materials repository. Returns null when the file can't be read (→ the UI's
 * UNSUPPORTED_CONTENT / no-extractable-text path).
 */
interface FileTextExtractor {
    fun extractText(bytes: ByteArray, mimeType: String): String?
}
