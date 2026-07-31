package evola.vocabulary.domain

data class ReviewOutcome(
    val qualityScore: Int,
    val wasCorrect: Boolean,
    val updatedSrsState: SrsState,
    val updatedCounters: MasteryCounters,
)

/** Composes deterministic grading + SM-2 scheduling into the one call the Application layer needs. */
object VocabularyReviewService {
    fun review(
        currentSrsState: SrsState,
        currentCounters: MasteryCounters,
        expectedAnswer: String,
        givenAnswer: String,
    ): ReviewOutcome {
        val quality = ReviewGrader.grade(expectedAnswer, givenAnswer)
        return applyQuality(currentSrsState, currentCounters, quality)
    }

    /** Used when a quality score already came from elsewhere (e.g. an AI evaluation), not Levenshtein grading. */
    fun applyQuality(currentSrsState: SrsState, currentCounters: MasteryCounters, quality: Int): ReviewOutcome {
        val updatedSrsState = Sm2Scheduler.schedule(currentSrsState, quality)
        val wasCorrect = quality >= 3
        val hadPriorRepetitions = currentSrsState.repetitions > 0
        return ReviewOutcome(
            qualityScore = quality,
            wasCorrect = wasCorrect,
            updatedSrsState = updatedSrsState,
            updatedCounters = currentCounters.applyOutcome(hadPriorRepetitions, wasCorrect),
        )
    }
}
