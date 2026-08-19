package evola.shared.vocabulary

import kotlin.test.Test
import kotlin.test.assertEquals

class VocabularyCsvTest {

    @Test
    fun `parses term and meaning pairs`() {
        val rows = parseWordCsv("Hund,dog\nKatze,cat")
        assertEquals(listOf(Triple("Hund", "dog", null), Triple("Katze", "cat", null)), rows)
    }

    @Test
    fun `parses an optional third native-meaning column`() {
        val rows = parseWordCsv("Hund,dog,chien")
        assertEquals(listOf(Triple("Hund", "dog", "chien")), rows)
    }

    @Test
    fun `skips blank lines and rows missing a required column`() {
        val rows = parseWordCsv("\nHund,dog\n\nBuch\n,meaning-only\nKatze,cat\n")
        assertEquals(listOf(Triple("Hund", "dog", null), Triple("Katze", "cat", null)), rows)
    }

    @Test
    fun `trims whitespace around every column`() {
        val rows = parseWordCsv("  Hund , dog , chien  ")
        assertEquals(listOf(Triple("Hund", "dog", "chien")), rows)
    }

    @Test
    fun `empty input yields no rows`() {
        assertEquals(emptyList(), parseWordCsv(""))
    }
}
