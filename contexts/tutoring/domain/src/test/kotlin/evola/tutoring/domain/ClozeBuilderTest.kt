package evola.tutoring.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClozeBuilderTest {

    @Test
    fun `blanks out an exact word match`() {
        val result = ClozeBuilder.blank("Ich lese das Buch.", "Buch")
        assertEquals("Ich lese das ______.", result)
    }

    @Test
    fun `is case-insensitive`() {
        val result = ClozeBuilder.blank("Ich lese das buch.", "Buch")
        assertEquals("Ich lese das ______.", result)
    }

    @Test
    fun `returns null when the word form is not present verbatim`() {
        val result = ClozeBuilder.blank("Ich lese die Bücher.", "Buch")
        assertNull(result)
    }
}
