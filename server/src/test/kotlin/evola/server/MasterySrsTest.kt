package evola.server

import kotlin.test.Test
import kotlin.test.assertEquals

class MasterySrsTest {

    @Test
    fun `correct answer advances mastery one stage and the interval one step`() {
        val state = MasterySrs.State(masteryState = "new", intervalIndex = 0, correctStreak = 0)
        val next = MasterySrs.onCorrect(state)
        assertEquals("learning", next.masteryState)
        assertEquals(1, next.intervalIndex)
        assertEquals(1, next.correctStreak)
    }

    @Test
    fun `correct answer caps mastery at mastered`() {
        val state = MasterySrs.State(masteryState = "mastered", intervalIndex = 4, correctStreak = 10)
        val next = MasterySrs.onCorrect(state)
        assertEquals("mastered", next.masteryState)
        assertEquals(4, next.intervalIndex)
        assertEquals(11, next.correctStreak)
    }

    @Test
    fun `incorrect answer drops mastery one stage and resets interval and streak`() {
        val state = MasterySrs.State(masteryState = "reviewing", intervalIndex = 3, correctStreak = 5)
        val next = MasterySrs.onIncorrect(state)
        assertEquals("learning", next.masteryState)
        assertEquals(0, next.intervalIndex)
        assertEquals(0, next.correctStreak)
    }

    @Test
    fun `incorrect answer floors mastery at new`() {
        val state = MasterySrs.State(masteryState = "new", intervalIndex = 0, correctStreak = 0)
        val next = MasterySrs.onIncorrect(state)
        assertEquals("new", next.masteryState)
        assertEquals(0, next.intervalIndex)
    }

    @Test
    fun `full progression from new to mastered takes exactly three correct answers`() {
        var state = MasterySrs.State(masteryState = "new", intervalIndex = 0, correctStreak = 0)
        repeat(3) { state = MasterySrs.onCorrect(state) }
        assertEquals("mastered", state.masteryState)
        assertEquals(3, state.intervalIndex)
    }

    @Test
    fun `intervalDaysFor maps the ladder correctly and clamps out-of-range indices`() {
        assertEquals(1L, MasterySrs.intervalDaysFor(0))
        assertEquals(35L, MasterySrs.intervalDaysFor(4))
        assertEquals(35L, MasterySrs.intervalDaysFor(99))
        assertEquals(1L, MasterySrs.intervalDaysFor(-5))
    }
}
