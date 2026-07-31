package evola.tutoring.domain

import evola.vocabulary.domain.MasteryStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveDifficultySelectorTest {

    @Test
    fun `NEW mastery with no history is BEGINNER`() {
        assertEquals(
            DifficultyTier.BEGINNER,
            AdaptiveDifficultySelector.selectTier(MasteryStatus.NEW, emptyList()),
        )
    }

    @Test
    fun `three correct in a row steps up from the baseline tier`() {
        assertEquals(
            DifficultyTier.INTERMEDIATE,
            AdaptiveDifficultySelector.selectTier(MasteryStatus.NEW, listOf(true, true, true)),
        )
    }

    @Test
    fun `two wrong in a row steps down from the baseline tier`() {
        assertEquals(
            DifficultyTier.BEGINNER, // already at floor, INTERMEDIATE baseline would step down to BEGINNER
            AdaptiveDifficultySelector.selectTier(MasteryStatus.NEEDS_PRACTICE, listOf(true, false, false)),
        )
    }

    @Test
    fun `mastered baseline does not exceed EXPERT`() {
        assertEquals(
            DifficultyTier.EXPERT,
            AdaptiveDifficultySelector.selectTier(MasteryStatus.MASTERED, listOf(true, true, true)),
        )
    }

    @Test
    fun `mixed recent outcomes keep the baseline tier`() {
        assertEquals(
            DifficultyTier.ADVANCED,
            AdaptiveDifficultySelector.selectTier(MasteryStatus.ALMOST_MASTERED, listOf(true, false, true)),
        )
    }
}
