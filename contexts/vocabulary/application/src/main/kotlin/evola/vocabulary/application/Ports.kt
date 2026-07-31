package evola.vocabulary.application

import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.core.kernel.VocabularyItemId
import evola.vocabulary.domain.LearnerVocabularyState
import evola.vocabulary.domain.ReviewHistoryEntry
import evola.vocabulary.domain.VocabularyItem
import java.time.Instant

interface VocabularyItemRepository {
    suspend fun findById(id: VocabularyItemId): VocabularyItem?

    /** Picks the next seed word the learner has no [LearnerVocabularyState] row for yet. */
    suspend fun findNextUnseenFor(learnerId: LearnerId): VocabularyItem?

    /** Inserts a new item — only ever used for learner-authored custom words (seed words arrive via Flyway). */
    suspend fun save(item: VocabularyItem)

    /** Idempotency check for vocabulary extraction: has this learner already added this word? */
    suspend fun findByOwnerAndWord(learnerId: LearnerId, germanWord: String): VocabularyItem?
}

interface LearnerVocabularyStateRepository {
    suspend fun findById(id: LearnerVocabularyStateId): LearnerVocabularyState?
    suspend fun findDueForLearner(learnerId: LearnerId, limit: Int, now: Instant = Instant.now()): List<LearnerVocabularyState>
    suspend fun findAllForLearner(learnerId: LearnerId): List<LearnerVocabularyState>
    suspend fun save(state: LearnerVocabularyState)
}

interface ReviewHistoryRepository {
    suspend fun record(entry: ReviewHistoryEntry)

    /** Most recent outcomes first, for [evola.tutoring.domain]'s adaptive difficulty selection. */
    suspend fun findRecentForState(learnerVocabularyStateId: LearnerVocabularyStateId, limit: Int): List<ReviewHistoryEntry>
}
