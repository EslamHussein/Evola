package evola.vocabulary.infrastructure

import evola.vocabulary.application.ReviewHistoryRepository
import evola.vocabulary.domain.ReviewHistoryEntry
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
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
}
