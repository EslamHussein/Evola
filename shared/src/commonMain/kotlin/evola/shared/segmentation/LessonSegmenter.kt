package evola.shared.segmentation

import kotlinx.serialization.Serializable

/** A lesson boundary within a material's full extracted text, before it becomes a DB row. */
data class RawSegment(
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val hasRealHeading: Boolean,
)

/** JSON shape of [RawSegment] for the local segmentation cache. */
@Serializable
data class RawSegmentJson(
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val hasRealHeading: Boolean,
)

fun RawSegment.toJson() = RawSegmentJson(title, startOffset, endOffset, hasRealHeading)
fun RawSegmentJson.toRaw() = RawSegment(title, startOffset, endOffset, hasRealHeading)

/**
 * Pure, dependency-free lesson-segmentation logic (01_PRODUCT_SPEC.md §1.6, 04_AI_PROMPTS.md §1) —
 * no LLM calls, no DB access. The on-device segmentation service tries [detectHeadings] first (skip
 * the LLM entirely when confident), falls back to chunked LLM calls via [chunkRanges] otherwise, and
 * always finishes with [mergeAndCap]. Ported verbatim from the server.
 */
object LessonSegmenter {
    const val MIN_LESSON_CHARS = 400
    const val MAX_LESSONS = 60
    const val CHUNK_SIZE_CHARS = 16_000
    const val CHUNK_OVERLAP_CHARS = 500
    const val MIN_HEADING_MARKERS = 2

    private val HEADING_PATTERNS = listOf(
        Regex("""(?i)^(kapitel|chapter|lektion|lesson|unit|teil|einheit)\s+\d+.*"""),
        Regex("""^\d{1,3}[.)]\s+\S.*"""),
        Regex("""^[A-ZÄÖÜ0-9][A-ZÄÖÜ0-9 \-]{2,59}$"""),
    )

    const val MAX_HEURISTIC_SEGMENT_CHARS = 20_000

    fun detectHeadings(text: String): List<RawSegment>? {
        val candidates = mutableListOf<Pair<Int, String>>()
        var offset = 0
        for (line in text.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && HEADING_PATTERNS.any { it.matches(trimmed) }) {
                candidates.add(offset to trimmed)
            }
            offset += line.length + 1
        }
        if (candidates.size < MIN_HEADING_MARKERS) return null
        for (i in 1 until candidates.size) {
            if (candidates[i].first - candidates[i - 1].first < MIN_LESSON_CHARS) return null
        }
        val lastSegmentLength = text.length - candidates.last().first
        if (lastSegmentLength > MAX_HEURISTIC_SEGMENT_CHARS) return null
        for (i in 1 until candidates.size) {
            if (candidates[i].first - candidates[i - 1].first > MAX_HEURISTIC_SEGMENT_CHARS) return null
        }
        return candidates.mapIndexed { index, (startOffset, title) ->
            val endOffset = if (index + 1 < candidates.size) candidates[index + 1].first else text.length
            RawSegment(title = title, startOffset = startOffset, endOffset = endOffset, hasRealHeading = true)
        }
    }

    fun chunkRanges(textLength: Int, fromOffset: Int = 0): List<IntRange> {
        if (textLength <= fromOffset) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = fromOffset
        while (start < textLength) {
            val end = minOf(start + CHUNK_SIZE_CHARS, textLength)
            ranges.add(start until end)
            if (end == textLength) break
            start = end - CHUNK_OVERLAP_CHARS
        }
        return ranges
    }

    fun mergeAndCap(rawSegments: List<RawSegment>): List<RawSegment> {
        if (rawSegments.size <= 1) return rawSegments
        val segments = rawSegments.sortedBy { it.startOffset }.toMutableList()

        var i = 0
        while (i < segments.size && segments.size > 1) {
            val seg = segments[i]
            if (seg.endOffset - seg.startOffset < MIN_LESSON_CHARS) {
                if (i + 1 < segments.size) {
                    val next = segments[i + 1]
                    segments[i + 1] = next.copy(startOffset = seg.startOffset)
                    segments.removeAt(i)
                } else {
                    val prev = segments[i - 1]
                    segments[i - 1] = prev.copy(endOffset = seg.endOffset)
                    segments.removeAt(i)
                }
            } else {
                i++
            }
        }

        while (segments.size > MAX_LESSONS) {
            var smallestIndex = 0
            var smallestCombinedSize = Int.MAX_VALUE
            for (j in 0 until segments.size - 1) {
                val combinedSize = segments[j + 1].endOffset - segments[j].startOffset
                if (combinedSize < smallestCombinedSize) {
                    smallestCombinedSize = combinedSize
                    smallestIndex = j
                }
            }
            segments[smallestIndex] = segments[smallestIndex].copy(endOffset = segments[smallestIndex + 1].endOffset)
            segments.removeAt(smallestIndex + 1)
        }

        return segments
    }
}
