package evola.server

/**
 * Shared mastery/SRS module (01_PRODUCT_SPEC.md §1.8, §2.1 - "Grammar (M7) reuses it unchanged").
 * Pure, dependency-free transitions over a generic mastery/interval/streak tuple so both
 * `vocabulary_progress` (M6) and `grammar_progress` (M7) can call the exact same functions against
 * their own tables' columns without this module knowing which one it's serving.
 */
object MasterySrs {
    /** Index into this ladder is `interval_index`; the value is days until next review. */
    val INTERVALS_DAYS = listOf(1L, 3L, 7L, 16L, 35L)

    /** Public so other progress-percentage calculations (e.g. Resource Details' completion ring)
     * can map a mastery_state to a stage index without duplicating this ladder. */
    val STAGES = listOf("new", "learning", "reviewing", "mastered")

    data class State(val masteryState: String, val intervalIndex: Int, val correctStreak: Int)

    /** Correct answer: mastery advances one stage (caps at "mastered"), interval ladder advances
     * one step (caps at the last, longest interval), streak increments. */
    fun onCorrect(state: State): State {
        val stageIndex = STAGES.indexOf(state.masteryState).coerceAtLeast(0)
        return State(
            masteryState = STAGES[(stageIndex + 1).coerceAtMost(STAGES.lastIndex)],
            intervalIndex = (state.intervalIndex + 1).coerceAtMost(INTERVALS_DAYS.lastIndex),
            correctStreak = state.correctStreak + 1,
        )
    }

    /** Incorrect answer: mastery drops one stage (floors at "new"), interval resets to the first
     * (shortest) rung, streak resets - per spec, "reset to the first interval on any incorrect
     * answer," not a full session restart. */
    fun onIncorrect(state: State): State {
        val stageIndex = STAGES.indexOf(state.masteryState).coerceAtLeast(0)
        return State(
            masteryState = STAGES[(stageIndex - 1).coerceAtLeast(0)],
            intervalIndex = 0,
            correctStreak = 0,
        )
    }

    fun intervalDaysFor(intervalIndex: Int): Long = INTERVALS_DAYS[intervalIndex.coerceIn(0, INTERVALS_DAYS.lastIndex)]

    /** Grammar-only (M7, 01_PRODUCT_SPEC.md §1.9): "a topic's mastery only advances after two
     * consecutive correct answers" - prevents guess-inflated progress on 2-option multiple choice.
     * Increments correctStreak only; masteryState/intervalIndex are untouched. Callers branch on
     * the CURRENT correctStreak's parity before calling: even (0,2,4...) means "first of a new
     * pair" -> call this; odd (1,3,5...) means "second of the pair" -> call [onCorrect] instead,
     * which advances mastery/interval and leaves correctStreak even again for the next pair.
     * [onIncorrect] is called unconditionally on any wrong answer, exactly as vocabulary does - it
     * resets correctStreak to 0, restarting pair-counting from scratch. */
    fun onPartialCorrect(state: State): State = state.copy(correctStreak = state.correctStreak + 1)
}
