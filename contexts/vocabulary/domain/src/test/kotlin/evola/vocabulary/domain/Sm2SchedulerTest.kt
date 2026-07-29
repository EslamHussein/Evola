package evola.vocabulary.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sm2SchedulerTest {

    @Test
    fun `first correct review sets interval to 1 day`() {
        val result = Sm2Scheduler.schedule(SrsState.INITIAL, quality = 5)
        assertEquals(1, result.intervalDays)
        assertEquals(1, result.repetitions)
    }

    @Test
    fun `second correct review sets interval to 6 days`() {
        val afterFirst = Sm2Scheduler.schedule(SrsState.INITIAL, quality = 5)
        val afterSecond = Sm2Scheduler.schedule(afterFirst, quality = 5)
        assertEquals(6, afterSecond.intervalDays)
        assertEquals(2, afterSecond.repetitions)
    }

    @Test
    fun `third correct review multiplies interval by the updated easiness factor`() {
        var state = SrsState.INITIAL
        state = Sm2Scheduler.schedule(state, quality = 5)
        state = Sm2Scheduler.schedule(state, quality = 5)
        val before = state
        state = Sm2Scheduler.schedule(state, quality = 5)

        // quality 5 -> EF increases by exactly 0.1 each call (see Sm2Scheduler formula)
        val expectedEf = before.easinessFactor + 0.1
        val expectedInterval = Math.round(before.intervalDays * expectedEf).toInt()
        assertEquals(expectedInterval, state.intervalDays)
        assertEquals(3, state.repetitions)
    }

    @Test
    fun `failing quality below 3 resets repetitions and interval`() {
        var state = SrsState.INITIAL
        state = Sm2Scheduler.schedule(state, quality = 5)
        state = Sm2Scheduler.schedule(state, quality = 5)
        val failed = Sm2Scheduler.schedule(state, quality = 1)
        assertEquals(0, failed.repetitions)
        assertEquals(1, failed.intervalDays)
    }

    @Test
    fun `easiness factor never drops below 1_3`() {
        var state = SrsState.INITIAL
        repeat(20) {
            state = Sm2Scheduler.schedule(state, quality = 3)
        }
        assertTrue(state.easinessFactor >= 1.3)
    }

    @Test
    fun `rejects out-of-range quality`() {
        assertEqualsThrows { Sm2Scheduler.schedule(SrsState.INITIAL, quality = 6) }
        assertEqualsThrows { Sm2Scheduler.schedule(SrsState.INITIAL, quality = -1) }
    }

    private fun assertEqualsThrows(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "expected IllegalArgumentException")
    }
}
