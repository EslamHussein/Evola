package evola.shared.core.common.srs

/**
 * Shared mastery/SRS module (01_PRODUCT_SPEC.md §1.8, §2.1). Pure, dependency-free transitions over
 * a generic mastery/interval/streak tuple so both vocabulary and grammar progress call the exact
 * same functions against their own rows. Ported verbatim from the server for the on-device
 * (serverless) architecture.
 */
object MasterySrs {
    /** Index into this ladder is `interval_index`; the value is days until next review. */
    val INTERVALS_DAYS = listOf(1L, 3L, 7L, 16L, 35L)

    /** Public so progress-percentage calculations can map a mastery_state to a stage index without
     * duplicating this ladder. */
    val STAGES = listOf("new", "learning", "reviewing", "mastered")

    data class State(val masteryState: String, val intervalIndex: Int, val correctStreak: Int)

    /** Correct answer: mastery advances one stage (caps at "mastered"), interval ladder advances
     * one step (caps at the last), streak increments. */
    fun onCorrect(state: State): State {
        val stageIndex = STAGES.indexOf(state.masteryState).coerceAtLeast(0)
        return State(
            masteryState = STAGES[(stageIndex + 1).coerceAtMost(STAGES.lastIndex)],
            intervalIndex = (state.intervalIndex + 1).coerceAtMost(INTERVALS_DAYS.lastIndex),
            correctStreak = state.correctStreak + 1,
        )
    }

    /** Incorrect answer: mastery drops one stage (floors at "new"), interval resets to the first
     * rung, streak resets. */
    fun onIncorrect(state: State): State {
        val stageIndex = STAGES.indexOf(state.masteryState).coerceAtLeast(0)
        return State(
            masteryState = STAGES[(stageIndex - 1).coerceAtLeast(0)],
            intervalIndex = 0,
            correctStreak = 0,
        )
    }

    fun intervalDaysFor(intervalIndex: Int): Long = INTERVALS_DAYS[intervalIndex.coerceIn(0, INTERVALS_DAYS.lastIndex)]

    /** Grammar-only (01_PRODUCT_SPEC.md §1.9): "a topic's mastery only advances after two
     * consecutive correct answers." Increments correctStreak only; masteryState/intervalIndex
     * untouched. Callers branch on the CURRENT correctStreak's parity (even → this; odd →
     * [onCorrect]); [onIncorrect] runs unconditionally on any wrong answer. */
    fun onPartialCorrect(state: State): State = state.copy(correctStreak = state.correctStreak + 1)
}
