package evola.vocabulary.infrastructure

import evola.core.kernel.LearnerVocabularyStateId
import evola.vocabulary.application.ReviewHistoryRepository
import evola.vocabulary.domain.ReviewHistoryEntry
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedReviewHistoryRepository(private val database: Database) : ReviewHistoryRepository {

    override suspend fun record(entry: ReviewHistoryEntry) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            ReviewHistoryTable.insert {
                it[id] = entry.id
                it[learnerVocabularyStateId] = entry.learnerVocabularyStateId.value
                it[reviewedAt] = entry.reviewedAt
                it[learnerAnswer] = entry.learnerAnswer
                it[wasCorrect] = entry.wasCorrect
                it[qualityScore] = entry.qualityScore
                it[efBefore] = entry.easinessFactorBefore
                it[efAfter] = entry.easinessFactorAfter
                it[intervalBefore] = entry.intervalDaysBefore
                it[intervalAfter] = entry.intervalDaysAfter
            }
        }
    }

    override suspend fun findRecentForState(learnerVocabularyStateId: LearnerVocabularyStateId, limit: Int): List<ReviewHistoryEntry> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            ReviewHistoryTable.selectAll()
                .where { ReviewHistoryTable.learnerVocabularyStateId eq learnerVocabularyStateId.value }
                .orderBy(ReviewHistoryTable.reviewedAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toEntry() }
        }

    private fun ResultRow.toEntry() = ReviewHistoryEntry(
        id = this[ReviewHistoryTable.id],
        learnerVocabularyStateId = LearnerVocabularyStateId(this[ReviewHistoryTable.learnerVocabularyStateId]),
        reviewedAt = this[ReviewHistoryTable.reviewedAt],
        learnerAnswer = this[ReviewHistoryTable.learnerAnswer],
        wasCorrect = this[ReviewHistoryTable.wasCorrect],
        qualityScore = this[ReviewHistoryTable.qualityScore],
        easinessFactorBefore = this[ReviewHistoryTable.efBefore],
        easinessFactorAfter = this[ReviewHistoryTable.efAfter],
        intervalDaysBefore = this[ReviewHistoryTable.intervalBefore],
        intervalDaysAfter = this[ReviewHistoryTable.intervalAfter],
    )
}
