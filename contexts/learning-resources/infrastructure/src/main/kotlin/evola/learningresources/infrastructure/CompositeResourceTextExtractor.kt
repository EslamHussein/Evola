package evola.learningresources.infrastructure

import evola.learningresources.application.ResourceTextExtractor
import evola.learningresources.domain.SourceType
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Deterministic, non-AI, I/O-bearing text extraction — correctly placed in infrastructure, not
 * domain/application. .docx is intentionally unsupported: adding it later is one enum case
 * (SourceType) plus one branch here, not a redesign.
 */
class CompositeResourceTextExtractor : ResourceTextExtractor {
    override fun extract(bytes: ByteArray, sourceType: SourceType): String = when (sourceType) {
        SourceType.PDF -> Loader.loadPDF(bytes).use { document -> PDFTextStripper().getText(document) }
        SourceType.TEXT_NOTE -> String(bytes, Charsets.UTF_8)
    }
}
