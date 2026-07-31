package evola.vocabulary.domain

/**
 * Deterministic answer grading — no LLM call. Cost/latency-free by design: correctness of a
 * fill-in-the-blank vocabulary answer doesn't need a model, just string comparison.
 */
object ReviewGrader {
    /** Returns an SM-2 quality score (0..5). */
    fun grade(expected: String, given: String): Int {
        val normalizedExpected = normalize(expected)
        val normalizedGiven = normalize(given)

        return when {
            normalizedGiven.isEmpty() -> 0
            normalizedExpected == normalizedGiven -> 5
            levenshtein(normalizedExpected, normalizedGiven) <= 1 -> 4
            else -> 2
        }
    }

    private fun normalize(s: String): String = s.trim().lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousRow = IntArray(b.length + 1) { it }
        var currentRow = IntArray(b.length + 1)

        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,
                    previousRow[j] + 1,
                    previousRow[j - 1] + cost,
                )
            }
            val tmp = previousRow
            previousRow = currentRow
            currentRow = tmp
        }
        return previousRow[b.length]
    }
}
