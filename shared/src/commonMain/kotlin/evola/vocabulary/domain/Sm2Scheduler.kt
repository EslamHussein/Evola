package evola.vocabulary.domain

import kotlin.math.roundToInt

/**
 * The classic SuperMemo-2 algorithm. Pure function, no I/O — spaced repetition scheduling is
 * deliberately deterministic and never AI-driven.
 *
 * @param quality 0..5 recall quality score (see [ReviewGrader]). Below 3 resets progress.
 */
object Sm2Scheduler {
    fun schedule(current: SrsState, quality: Int): SrsState {
        require(quality in 0..5) { "quality must be in 0..5, was $quality" }

        if (quality < 3) {
            return current.copy(repetitions = 0, intervalDays = 1)
        }

        val newEasinessFactor = (
            current.easinessFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        ).coerceAtLeast(1.3)

        val newRepetitions = current.repetitions + 1
        val newIntervalDays = when (newRepetitions) {
            1 -> 1
            2 -> 6
            else -> (current.intervalDays * newEasinessFactor).roundToInt()
        }

        return current.copy(
            easinessFactor = newEasinessFactor,
            intervalDays = newIntervalDays,
            repetitions = newRepetitions,
        )
    }
}
