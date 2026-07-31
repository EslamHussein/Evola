package evola.vocabulary.domain

/**
 * Denormalized attempt/lapse counters, tracked alongside [SrsState] because SRS state alone
 * can't distinguish "brand new" from "learned then forgot" (both reset `repetitions` to 0).
 */
data class MasteryCounters(
    val totalAttempts: Int = 0,
    val totalLapses: Int = 0,
    val consecutiveCorrect: Int = 0,
) {
    /** @param hadPriorRepetitions whether the word had ever been correctly recalled before this attempt. */
    fun applyOutcome(hadPriorRepetitions: Boolean, wasCorrect: Boolean): MasteryCounters {
        val isLapse = !wasCorrect && hadPriorRepetitions
        return copy(
            totalAttempts = totalAttempts + 1,
            totalLapses = totalLapses + if (isLapse) 1 else 0,
            consecutiveCorrect = if (wasCorrect) consecutiveCorrect + 1 else 0,
        )
    }

    companion object {
        val INITIAL = MasteryCounters()
    }
}
