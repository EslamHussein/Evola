package evola.shared.feature.vocabulary.domain

/**
 * Pure, dependency-free vocabulary SRS ladder for the Lingvist-style flat-queue engine — deliberately
 * separate from [evola.shared.core.common.srs.MasterySrs] (which remains Grammar-only) so the 5-status
 * vocabulary lifecycle and Grammar's 4-stage ladder never share a data model, even though the
 * transition shape (index +/-1, capped/floored) is the same idea.
 */
object VocabularySrs {
    /** Index into this ladder is `interval_index`; the value is days until next review. */
    val INTERVALS_DAYS = listOf(1L, 3L, 7L, 14L, 30L)

    /** Full lifecycle, in order. [onCorrect]/[onIncorrect] only ever operate within the gradable
     * sub-ladder (learning/review/mastered) — "unseen"/"introduced" are set exclusively via
     * [introduce]. */
    val STATUSES = listOf("unseen", "introduced", "learning", "review", "mastered")
    private val GRADABLE_STATUSES = listOf("learning", "review", "mastered")

    data class State(val status: String, val intervalIndex: Int, val correctStreak: Int, val incorrectStreak: Int = 0)

    /** unseen/introduced → "introduced". Called once, when the Intro card's "Got it" is tapped. */
    fun introduce(state: State): State = state.copy(status = "introduced")

    /** Correct answer. If the word was still "introduced" (its first-ever drill), it always exits to
     * "learning" - never skips ahead - regardless of how well the first attempt went; otherwise the
     * gradable ladder advances one rung (capped at "mastered"). Interval ladder advances one rung
     * (capped at 30 days), correct_streak increments, incorrect_streak resets. */
    fun onCorrect(state: State): State {
        val nextStatus = if (state.status == "introduced") {
            "learning"
        } else {
            val idx = GRADABLE_STATUSES.indexOf(state.status).coerceAtLeast(0)
            GRADABLE_STATUSES[(idx + 1).coerceAtMost(GRADABLE_STATUSES.lastIndex)]
        }
        return state.copy(
            status = nextStatus,
            intervalIndex = (state.intervalIndex + 1).coerceAtMost(INTERVALS_DAYS.lastIndex),
            correctStreak = state.correctStreak + 1,
            incorrectStreak = 0,
        )
    }

    /** Incorrect answer. If the word was "introduced" (first drill, wrong), it still exits to
     * "learning" (the floor) - it does NOT regress to "unseen". Otherwise the gradable ladder drops
     * one rung, floored at "learning" (mastered->review, review->learning, learning stays learning).
     * Interval resets to the first rung (1 day), correct_streak resets, incorrect_streak increments. */
    fun onIncorrect(state: State): State {
        val nextStatus = if (state.status == "introduced") {
            "learning"
        } else {
            val idx = GRADABLE_STATUSES.indexOf(state.status).coerceAtLeast(0)
            GRADABLE_STATUSES[(idx - 1).coerceAtLeast(0)]
        }
        return state.copy(status = nextStatus, intervalIndex = 0, correctStreak = 0, incorrectStreak = state.incorrectStreak + 1)
    }

    fun intervalDaysFor(intervalIndex: Int): Long = INTERVALS_DAYS[intervalIndex.coerceIn(0, INTERVALS_DAYS.lastIndex)]
}
