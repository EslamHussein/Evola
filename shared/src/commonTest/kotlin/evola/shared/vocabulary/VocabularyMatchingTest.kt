package evola.shared.vocabulary

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VocabularyMatchingTest {

    @Test
    fun `exact match is tolerant`() {
        assertTrue(isTolerantMatch("die Bewerbung", "die Bewerbung"))
    }

    @Test
    fun `case-insensitive match is tolerant`() {
        assertTrue(isTolerantMatch("Hund", "hund"))
        assertTrue(isTolerantMatch("HUND", "hund"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(isTolerantMatch("Hund", "  hund  "))
    }

    @Test
    fun `single substitution is tolerant`() {
        assertTrue(isTolerantMatch("Hund", "Hend"))
    }

    @Test
    fun `single insertion is tolerant`() {
        assertTrue(isTolerantMatch("Hund", "Hundd"))
    }

    @Test
    fun `single deletion is tolerant`() {
        assertTrue(isTolerantMatch("Hund", "Hnd"))
    }

    @Test
    fun `two-character edit distance is not tolerant`() {
        assertFalse(isTolerantMatch("Hund", "Hasd"))
    }

    @Test
    fun `completely different words are not tolerant`() {
        assertFalse(isTolerantMatch("Hund", "Katze"))
    }

    @Test
    fun `empty response against a real word is not tolerant`() {
        assertFalse(isTolerantMatch("Hund", ""))
    }
}
