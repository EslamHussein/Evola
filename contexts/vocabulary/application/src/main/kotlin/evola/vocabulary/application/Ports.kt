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
}

interface LearnerVocabularyStateRepository {
    suspend fun findById(id: LearnerVocabularyStateId): LearnerVocabularyState?
    suspend fun findDueForLearner(learnerId: LearnerId, limit: Int, now: Instant = Instant.now()): List<LearnerVocabularyState>
    suspend fun save(state: LearnerVocabularyState)
}

interface ReviewHistoryRepository {
    suspend fun record(entry: ReviewHistoryEntry)
}
