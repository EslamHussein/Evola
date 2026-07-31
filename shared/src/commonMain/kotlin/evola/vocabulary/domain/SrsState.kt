package evola.vocabulary.domain

/**
 * Pure SM-2 scheduling state. Deliberately generic (no notion of "word") so it can later be
 * reused by a Grammar Mastery context without depending on Vocabulary.
 */
data class SrsState(
    val easinessFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
) {
    companion object {
        val INITIAL = SrsState()
    }
}
