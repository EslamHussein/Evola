package evola.vocabulary.domain

import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.core.kernel.VocabularyItemId
import java.time.Instant

data class LearnerVocabularyState(
    val id: LearnerVocabularyStateId,
    val learnerId: LearnerId,
    val vocabularyItemId: VocabularyItemId,
    val srsState: SrsState,
    val counters: MasteryCounters,
    val nextReviewAt: Instant,
    val lastReviewedAt: Instant?,
) {
    val status: MasteryStatus get() = MasteryStatus.deriveFrom(srsState, counters)

    val isDue: Boolean get() = !nextReviewAt.isAfter(Instant.now())

    companion object {
        fun newFor(
            id: LearnerVocabularyStateId,
            learnerId: LearnerId,
            vocabularyItemId: VocabularyItemId,
            now: Instant = Instant.now(),
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
