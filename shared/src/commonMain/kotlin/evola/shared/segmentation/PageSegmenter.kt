package evola.shared.segmentation

import evola.shared.core.common.PAGE_BREAK

/**
 * "Split by page" organization mode: no AI call at all (unlike [LessonSegmenter]'s LLM fallback) -
 * splits the material's stored text on [PAGE_BREAK] (written by the platform PDF extractors, one
 * marker per page boundary) and drops pages that are just front/back matter rather than lesson
 * content, then merges/caps the survivors via [LessonSegmenter.mergeAndCap] so a book with many
 * thin pages still produces reasonably-sized lessons instead of one lesson per page.
 */
object PageSegmenter {
    private val TOC_KEYWORDS = listOf(
        "table of contents", "contents", "index", "inhaltsverzeichnis", "impressum",
        "bibliography", "glossary", "appendix", "references",
    )
    private val DOT_LEADER_LINE = Regex("""^.+\.{3,}\s*\d+$""")
    private const val MIN_PAGE_CHARS = 80
    private const val MIN_DOT_LEADER_LINES = 3

    fun segment(text: String): List<RawSegment> {
        val pages = text.split(PAGE_BREAK)
        val segments = mutableListOf<RawSegment>()
        var offset = 0
        pages.forEachIndexed { index, page ->
            val start = offset
            val end = offset + page.length
            if (!isFrontOrBackMatter(page)) {
                segments.add(RawSegment(title = "Page ${index + 1}", startOffset = start, endOffset = end, hasRealHeading = false))
            }
            offset = end + PAGE_BREAK.length
        }
        return LessonSegmenter.mergeAndCap(segments)
    }

    /** Blank/cover pages (too short to be real content), and pages that read as table-of-contents
     * or index matter (a short, keyword-titled page, or a page dominated by "title .... 42"-style
     * dot-leader lines) - both common in uploaded books/notes but never worth a lesson. */
    private fun isFrontOrBackMatter(page: String): Boolean {
        val trimmed = page.trim()
        if (trimmed.length < MIN_PAGE_CHARS) return true

        val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.lowercase() ?: ""
        if (TOC_KEYWORDS.any { firstLine == it || firstLine.startsWith("$it ") }) return true

        val dotLeaderLines = trimmed.lineSequence().count { DOT_LEADER_LINE.matches(it.trim()) }
        return dotLeaderLines >= MIN_DOT_LEADER_LINES
    }
}
