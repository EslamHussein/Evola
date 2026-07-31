package evola.vocabulary.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class LearnerVocabularyState(
    val id: String,
    val learnerId: String,
    val vocabularyItemId: String,
    val srsState: SrsState,
    val counters: MasteryCounters,
    val nextReviewAt: Instant,
    val lastReviewedAt: Instant?,
) {
    val status: MasteryStatus get() = MasteryStatus.deriveFrom(srsState, counters)

    val isDue: Boolean get() = nextReviewAt <= Clock.System.now()

    companion object {
        fun newFor(
            id: String,
            learnerId: String,
            vocabularyItemId: String,
            now: Instant = Clock.System.now(),
        ) = LearnerVocabularyState(
            id = id,
            learnerId = learnerId,
            vocabularyItemId = vocabularyItemId,
            srsState = SrsState.INITIAL,
            counters = MasteryCounters.INITIAL,
            nextReviewAt = now,
            lastReviewedAt = null,
        )
    }
}
