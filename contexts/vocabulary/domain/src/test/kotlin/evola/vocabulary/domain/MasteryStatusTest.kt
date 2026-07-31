package evola.vocabulary.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MasteryStatusTest {

    @Test
    fun `brand new word with zero attempts is NEW`() {
        assertEquals(MasteryStatus.NEW, MasteryStatus.deriveFrom(SrsState.INITIAL, MasteryCounters.INITIAL))
    }

    @Test
    fun `two or more lapses is NEEDS_PRACTICE regardless of interval`() {
        val srs = SrsState(intervalDays = 90)
        val counters = MasteryCounters(totalAttempts = 5, totalLapses = 2)
        assertEquals(MasteryStatus.NEEDS_PRACTICE, MasteryStatus.deriveFrom(srs, counters))
    }

    @Test
    fun `short interval with no lapses is LEARNING`() {
        val srs = SrsState(intervalDays = 6)
        val counters = MasteryCounters(totalAttempts = 2, totalLapses = 0)
        assertEquals(MasteryStatus.LEARNING, MasteryStatus.deriveFrom(srs, counters))
    }

    @Test
    fun `mid interval with no lapses is ALMOST_MASTERED`() {
        val srs = SrsState(intervalDays = 30)
        val counters = MasteryCounters(totalAttempts = 3, totalLapses = 0)
        assertEquals(MasteryStatus.ALMOST_MASTERED, MasteryStatus.deriveFrom(srs, counters))
    }

    @Test
    fun `long interval with no lapses is MASTERED`() {
        val srs = SrsState(intervalDays = 90)
        val counters = MasteryCounters(totalAttempts = 4, totalLapses = 0)
        assertEquals(MasteryStatus.MASTERED, MasteryStatus.deriveFrom(srs, counters))
    }
}
