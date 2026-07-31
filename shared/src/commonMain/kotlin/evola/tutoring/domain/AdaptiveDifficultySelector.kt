package evola.tutoring.domain

import evola.vocabulary.domain.MasteryStatus

/**
 * Deterministic, no LLM call: mastery status gives the long-run baseline tier ("based on the
 * user's level"), the last few outcomes give short-run adaptivity ("and previous answers"). The
 * LLM is only ever invoked afterward, to render content for the tier this function already chose.
 */
object AdaptiveDifficultySelector {
    fun selectTier(masteryStatus: MasteryStatus, recentOutcomes: List<Boolean>): DifficultyTier {
        val base = when (masteryStatus) {
            MasteryStatus.NEW, MasteryStatus.LEARNING -> DifficultyTier.BEGINNER
            MasteryStatus.NEEDS_PRACTICE -> DifficultyTier.INTERMEDIATE
            MasteryStatus.ALMOST_MASTERED -> DifficultyTier.ADVANCED
            MasteryStatus.MASTERED -> DifficultyTier.EXPERT
        }
        val lastThree = recentOutcomes.takeLast(3)
        val lastTwo = lastThree.takeLast(2)
        return when {
            lastTwo.size == 2 && lastTwo.none { it } -> base.stepDown()
            lastThree.size == 3 && lastThree.all { it } -> base.stepUp()
            else -> base
        }
    }
}
