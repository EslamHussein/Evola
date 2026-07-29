package evola.vocabulary.domain

data class ReviewOutcome(
    val qualityScore: Int,
    val wasCorrect: Boolean,
    val updatedSrsState: SrsState,
)

/** Composes deterministic grading + SM-2 scheduling into the one call the Application layer needs. */
object VocabularyReviewService {
    fun review(currentSrsState: SrsState, expectedAnswer: String, givenAnswer: String): ReviewOutcome {
        val quality = ReviewGrader.grade(expectedAnswer, givenAnswer)
        val updated = Sm2Scheduler.schedule(currentSrsState, quality)
        return ReviewOutcome(
            qualityScore = quality,
            wasCorrect = quality >= 3,
            updatedSrsState = updated,
        )
    }
}
