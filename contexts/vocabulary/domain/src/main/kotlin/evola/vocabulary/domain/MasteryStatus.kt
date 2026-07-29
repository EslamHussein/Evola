package evola.vocabulary.domain

enum class MasteryStatus {
    NEW,
    LEARNING,
    REVIEWING,
    MASTERED;

    companion object {
        /** Denormalized status derived purely from SRS state, for cheap querying/display. */
        fun deriveFrom(srsState: SrsState): MasteryStatus = when {
            srsState.repetitions == 0 -> NEW
            srsState.intervalDays < 21 -> LEARNING
            srsState.intervalDays < 60 -> REVIEWING
            else -> MASTERED
        }
    }
}
