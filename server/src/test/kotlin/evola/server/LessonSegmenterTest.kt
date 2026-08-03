package evola.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonSegmenterTest {

    @Test
    fun `detects clear chapter headings and splits directly along them`() {
        val text = buildString {
            append("Kapitel 1\n")
            append("x".repeat(500))
            append("\n")
            append("Kapitel 2\n")
            append("y".repeat(500))
        }

        val segments = LessonSegmenter.detectHeadings(text)

        assertEquals(2, segments?.size)
        assertEquals("Kapitel 1", segments!![0].title)
        assertEquals("Kapitel 2", segments[1].title)
        assertEquals(0, segments[0].startOffset)
        assertEquals(segments[1].startOffset, segments[0].endOffset)
        assertTrue(segments.all { it.hasRealHeading })
    }

    @Test
    fun `returns null when fewer than two heading markers are found`() {
        val text = "Kapitel 1\n" + "x".repeat(500)
        assertNull(LessonSegmenter.detectHeadings(text))
    }

    @Test
    fun `returns null when candidate headings are too close together (false positives)`() {
        // Two short ALL-CAPS-looking lines right next to each other - not real chapter breaks.
        val text = "TITLE ONE\nTITLE TWO\n" + "x".repeat(500)
        assertNull(LessonSegmenter.detectHeadings(text))
    }

    @Test
    fun `returns null when a would-be segment is implausibly large (title-page false positive)`() {
        // Regression for the real A2 - Wortschatz.pdf production bug: a stylized title-page line
        // ("G L O S S A R") and an edition line ("1. Auflage") matched the heading regexes and were
        // spaced far enough apart, but everything between/after them was one giant 100k+ char blob -
        // not real chapter structure. Must fall through to the LLM chunked path instead.
        val text = "G L O S S A R\n" + "x".repeat(5000) + "\n1. Auflage\n" + "y".repeat(100_000)
        assertNull(LessonSegmenter.detectHeadings(text))
    }

    @Test
    fun `does not match on ordinary prose`() {
        val text = "Der Hund läuft schnell durch den Park und spielt mit dem Ball im Sonnenschein.\n".repeat(20)
        assertNull(LessonSegmenter.detectHeadings(text))
    }

    @Test
    fun `mergeAndCap merges a too-short segment into its following neighbor`() {
        val segments = listOf(
            RawSegment("Tiny", 0, 50, true),
            RawSegment("Real Lesson", 50, 2000, true),
        )

        val merged = LessonSegmenter.mergeAndCap(segments)

        assertEquals(1, merged.size)
        assertEquals("Real Lesson", merged[0].title)
        assertEquals(0, merged[0].startOffset)
        assertEquals(2000, merged[0].endOffset)
    }

    @Test
    fun `mergeAndCap merges a trailing too-short segment into the previous one`() {
        val segments = listOf(
            RawSegment("Real Lesson", 0, 2000, true),
            RawSegment("Tiny Tail", 2000, 2050, true),
        )

        val merged = LessonSegmenter.mergeAndCap(segments)

        assertEquals(1, merged.size)
        assertEquals("Real Lesson", merged[0].title)
        assertEquals(2050, merged[0].endOffset)
    }

    @Test
    fun `mergeAndCap leaves well-sized segments untouched`() {
        val segments = listOf(
            RawSegment("Lesson 1", 0, 1000, true),
            RawSegment("Lesson 2", 1000, 2000, true),
            RawSegment("Lesson 3", 2000, 3000, true),
        )

        val merged = LessonSegmenter.mergeAndCap(segments)

        assertEquals(3, merged.size)
    }

    @Test
    fun `mergeAndCap caps at 60 lessons by merging the smallest adjacent pairs`() {
        // 70 equal-sized 1000-char segments, well above MIN_LESSON_CHARS individually.
        val segments = (0 until 70).map { i -> RawSegment("Lesson ${i + 1}", i * 1000, (i + 1) * 1000, true) }

        val merged = LessonSegmenter.mergeAndCap(segments)

        assertEquals(LessonSegmenter.MAX_LESSONS, merged.size)
        // Still contiguous and covering the full original range.
        assertEquals(0, merged.first().startOffset)
        assertEquals(70_000, merged.last().endOffset)
        for (i in 1 until merged.size) {
            assertEquals(merged[i - 1].endOffset, merged[i].startOffset)
        }
    }

    @Test
    fun `chunkRanges splits text into overlapping ranges of the configured size`() {
        val textLength = 40_000
        val ranges = LessonSegmenter.chunkRanges(textLength)

        assertTrue(ranges.isNotEmpty())
        assertEquals(0, ranges.first().first)
        assertEquals(textLength - 1, ranges.last().last)
        for (i in 1 until ranges.size) {
            assertTrue(ranges[i].first < ranges[i - 1].last, "expected overlap between consecutive chunks")
        }
    }

    @Test
    fun `chunkRanges from a nonzero offset only covers the remainder`() {
        val ranges = LessonSegmenter.chunkRanges(textLength = 40_000, fromOffset = 30_000)
        assertTrue(ranges.isNotEmpty())
        assertEquals(30_000, ranges.first().first)
        assertEquals(39_999, ranges.last().last)
    }

    @Test
    fun `chunkRanges returns empty when already past the end`() {
        assertEquals(emptyList(), LessonSegmenter.chunkRanges(textLength = 100, fromOffset = 100))
    }
}
