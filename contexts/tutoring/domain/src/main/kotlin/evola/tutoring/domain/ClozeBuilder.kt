package evola.tutoring.domain

/**
 * Builds a cloze (fill-in-the-blank) sentence from an already-cached example sentence, avoiding a
 * second AI call for the common case. Returns null if the exact word form isn't found verbatim —
 * the caller then falls back to AI generation.
 */
object ClozeBuilder {
    fun blank(sentence: String, targetWord: String): String? {
        val regex = Regex("(?i)\\b${Regex.escape(targetWord)}\\b")
        if (!regex.containsMatchIn(sentence)) return null
        return regex.replace(sentence, "______")
    }
}
