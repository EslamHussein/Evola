package evola.vocabulary.domain

enum class MasteryStatus {
    NEW,
    LEARNING,
    NEEDS_PRACTICE,
    ALMOST_MASTERED,
    MASTERED;

    companion object {
        /** Denormalized status derived from SRS timing plus attempt/lapse counters, for cheap querying/display. */
        fun deriveFrom(srsState: SrsState, counters: MasteryCounters): MasteryStatus = when {
            counters.totalAttempts == 0 -> NEW
            counters.totalLapses >= 2 -> NEEDS_PRACTICE
            srsState.intervalDays < 21 -> LEARNING
            srsState.intervalDays < 60 -> ALMOST_MASTERED
            else -> MASTERED
        }
    }
}
