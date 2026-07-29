package evola.vocabulary.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewGraderTest {

    @Test
    fun `exact match scores 5`() {
        assertEquals(5, ReviewGrader.grade("house", "house"))
    }

    @Test
    fun `case and whitespace insensitive exact match scores 5`() {
        assertEquals(5, ReviewGrader.grade("House", "  house  "))
    }

    @Test
    fun `umlaut ascii transliteration counts as exact match`() {
        assertEquals(5, ReviewGrader.grade("Fussball", "fussball"))
        assertEquals(5, ReviewGrader.grade("Straße", "strasse"))
    }

    @Test
    fun `single typo scores 4`() {
        assertEquals(4, ReviewGrader.grade("house", "hous"))
        assertEquals(4, ReviewGrader.grade("house", "houze"))
    }

    @Test
    fun `wrong answer scores 2`() {
        assertEquals(2, ReviewGrader.grade("house", "car"))
    }

    @Test
    fun `empty answer scores 0`() {
        assertEquals(0, ReviewGrader.grade("house", ""))
        assertEquals(0, ReviewGrader.grade("house", "   "))
    }
}
