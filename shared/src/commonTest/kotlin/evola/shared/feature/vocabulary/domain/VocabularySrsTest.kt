package evola.shared.feature.vocabulary.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class VocabularySrsTest {

    @Test
    fun `introduce sets status to introduced without touching interval or streaks`() {
        val state = VocabularySrs.State(status = "unseen", intervalIndex = 0, correctStreak = 0)
        val next = VocabularySrs.introduce(state)
        assertEquals("introduced", next.status)
        assertEquals(0, next.intervalIndex)
        assertEquals(0, next.correctStreak)
    }

    @Test
    fun `first correct drill from introduced exits to learning`() {
        val state = VocabularySrs.State(status = "introduced", intervalIndex = 0, correctStreak = 0)
        val next = VocabularySrs.onCorrect(state)
        assertEquals("learning", next.status)
        assertEquals(1, next.intervalIndex)
        assertEquals(1, next.correctStreak)
        assertEquals(0, next.incorrectStreak)
    }

    @Test
    fun `first incorrect drill from introduced still exits to learning, not back to unseen`() {
        val state = VocabularySrs.State(status = "introduced", intervalIndex = 0, correctStreak = 0)
        val next = VocabularySrs.onIncorrect(state)
        assertEquals("learning", next.status)
        assertEquals(0, next.intervalIndex)
        assertEquals(0, next.correctStreak)
        assertEquals(1, next.incorrectStreak)
    }

    @Test
    fun `correct answers climb the gradable ladder learning to review to mastered`() {
        var state = VocabularySrs.State(status = "learning", intervalIndex = 0, correctStreak = 0)
        state = VocabularySrs.onCorrect(state)
        assertEquals("review", state.status)
        state = VocabularySrs.onCorrect(state)
        assertEquals("mastered", state.status)
        // Capped at mastered - another correct answer doesn't advance status further.
        state = VocabularySrs.onCorrect(state)
        assertEquals("mastered", state.status)
    }

    @Test
    fun `incorrect answers regress the gradable ladder mastered to review to learning`() {
        var state = VocabularySrs.State(status = "mastered", intervalIndex = 4, correctStreak = 5)
        state = VocabularySrs.onIncorrect(state)
        assertEquals("review", state.status)
        assertEquals(0, state.intervalIndex)
        assertEquals(0, state.correctStreak)
        assertEquals(1, state.incorrectStreak)
        state = VocabularySrs.onIncorrect(state)
        assertEquals("learning", state.status)
    }

    @Test
    fun `learning is the floor - an incorrect answer while learning does not regress further`() {
        val state = VocabularySrs.State(status = "learning", intervalIndex = 2, correctStreak = 3)
        val next = VocabularySrs.onIncorrect(state)
        assertEquals("learning", next.status)
        assertEquals(0, next.intervalIndex)
        assertEquals(0, next.correctStreak)
        assertEquals(1, next.incorrectStreak)
    }

    @Test
    fun `interval index is capped at the last rung`() {
        var state = VocabularySrs.State(status = "review", intervalIndex = VocabularySrs.INTERVALS_DAYS.lastIndex, correctStreak = 0)
        state = VocabularySrs.onCorrect(state)
        assertEquals(VocabularySrs.INTERVALS_DAYS.lastIndex, state.intervalIndex)
    }

    @Test
    fun `intervalDaysFor maps index to the correct day count and clamps out-of-range indices`() {
        assertEquals(1L, VocabularySrs.intervalDaysFor(0))
        assertEquals(3L, VocabularySrs.intervalDaysFor(1))
        assertEquals(7L, VocabularySrs.intervalDaysFor(2))
        assertEquals(14L, VocabularySrs.intervalDaysFor(3))
        assertEquals(30L, VocabularySrs.intervalDaysFor(4))
        assertEquals(1L, VocabularySrs.intervalDaysFor(-1))
        assertEquals(30L, VocabularySrs.intervalDaysFor(99))
    }
}
