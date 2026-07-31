package evola.vocabulary.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MasteryCountersTest {

    @Test
    fun `correct answer increments attempts and consecutive correct with no lapse`() {
        val result = MasteryCounters.INITIAL.applyOutcome(hadPriorRepetitions = false, wasCorrect = true)
        assertEquals(1, result.totalAttempts)
        assertEquals(0, result.totalLapses)
        assertEquals(1, result.consecutiveCorrect)
    }

    @Test
    fun `wrong answer on a brand new word is not a lapse`() {
        val result = MasteryCounters.INITIAL.applyOutcome(hadPriorRepetitions = false, wasCorrect = false)
        assertEquals(1, result.totalAttempts)
        assertEquals(0, result.totalLapses)
        assertEquals(0, result.consecutiveCorrect)
    }

    @Test
    fun `wrong answer after prior success is a lapse and resets streak`() {
        val afterOneCorrect = MasteryCounters.INITIAL.applyOutcome(hadPriorRepetitions = false, wasCorrect = true)
        val result = afterOneCorrect.applyOutcome(hadPriorRepetitions = true, wasCorrect = false)
        assertEquals(2, result.totalAttempts)
        assertEquals(1, result.totalLapses)
        assertEquals(0, result.consecutiveCorrect)
    }
}
