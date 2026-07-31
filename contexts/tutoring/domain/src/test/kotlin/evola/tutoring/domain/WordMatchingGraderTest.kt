package evola.tutoring.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class WordMatchingGraderTest {

    private val correct = listOf("Hund" to "dog", "Katze" to "cat", "Haus" to "house", "Baum" to "tree")

    @Test
    fun `all pairs matched scores 5`() {
        assertEquals(5, WordMatchingGrader.grade(correct, correct))
    }

    @Test
    fun `case and whitespace insensitive`() {
        val submitted = listOf(" hund " to "DOG", "katze" to "Cat", "Haus" to "house", "Baum" to "tree")
        assertEquals(5, WordMatchingGrader.grade(correct, submitted))
    }

    @Test
    fun `three of four correct is below the 80pct threshold and scores 2`() {
        val submitted = listOf("Hund" to "dog", "Katze" to "cat", "Haus" to "house", "Baum" to "wrong")
        assertEquals(2, WordMatchingGrader.grade(correct, submitted))
    }

    @Test
    fun `four of five correct meets the 80pct threshold and scores 4`() {
        val fivePairs = correct + ("Vogel" to "bird")
        val submitted = listOf("Hund" to "dog", "Katze" to "cat", "Haus" to "house", "Baum" to "tree", "Vogel" to "wrong")
        assertEquals(4, WordMatchingGrader.grade(fivePairs, submitted))
    }

    @Test
    fun `half correct scores 2`() {
        val submitted = listOf("Hund" to "dog", "Katze" to "cat", "Haus" to "wrong", "Baum" to "wrong")
        assertEquals(2, WordMatchingGrader.grade(correct, submitted))
    }

    @Test
    fun `nothing matched scores 0`() {
        assertEquals(0, WordMatchingGrader.grade(correct, emptyList()))
    }

    @Test
    fun `empty correct set scores 0`() {
        assertEquals(0, WordMatchingGrader.grade(emptyList(), correct))
    }
}
