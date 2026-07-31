package evola.tutoring.domain

/**
 * Deterministic grading for WORD_MATCHING exercises — no LLM call, same spirit as vocabulary's
 * ReviewGrader. Uses plain String pairs (not the ai-gateway MatchPair DTO) so the domain layer
 * stays free of any dependency on an integrations module.
 */
object WordMatchingGrader {
    /** Returns an SM-2 quality score (0..5) based on the fraction of pairs matched correctly. */
    fun grade(correctPairs: List<Pair<String, String>>, submittedPairs: List<Pair<String, String>>): Int {
        if (correctPairs.isEmpty()) return 0
        val correctSet = correctPairs.map { normalize(it.first) to normalize(it.second) }.toSet()
        val submittedSet = submittedPairs.map { normalize(it.first) to normalize(it.second) }.toSet()
        val matchedCount = submittedSet.count { it in correctSet }
        val fraction = matchedCount.toDouble() / correctPairs.size
        return when {
            fraction >= 1.0 -> 5
            fraction >= 0.8 -> 4
            fraction >= 0.5 -> 2
            else -> 0
        }
    }

    private fun normalize(s: String): String = s.trim().lowercase()
}
