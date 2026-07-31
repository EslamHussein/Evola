package evola.vocabulary.infrastructure

import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.core.kernel.VocabularyItemId
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.domain.LearnerVocabularyState
import evola.vocabulary.domain.MasteryCounters
import evola.vocabulary.domain.SrsState
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedLearnerVocabularyStateRepository(private val database: Database) : LearnerVocabularyStateRepository {

    override suspend fun findById(id: LearnerVocabularyStateId): LearnerVocabularyState? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearnerVocabularyStateTable.selectAll().where { LearnerVocabularyStateTable.id eq id.value }
                .singleOrNull()?.toState()
        }

    override suspend fun findDueForLearner(learnerId: LearnerId, limit: Int, now: Instant): List<LearnerVocabularyState> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearnerVocabularyStateTable.selectAll().where {
                (LearnerVocabularyStateTable.learnerId eq learnerId.value) and
                    (LearnerVocabularyStateTable.nextReviewAt lessEq now)
            }.orderBy(LearnerVocabularyStateTable.nextReviewAt, SortOrder.ASC)
                .limit(limit)
                .map { it.toState() }
        }

    override suspend fun save(state: LearnerVocabularyState) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = LearnerVocabularyStateTable.selectAll()
                .where { LearnerVocabularyStateTable.id eq state.id.value }
                .any()

            if (exists) {
                LearnerVocabularyStateTable.update({ LearnerVocabularyStateTable.id eq state.id.value }) {
                    it[easinessFactor] = state.srsState.easinessFactor
                    it[intervalDays] = state.srsState.intervalDays
                    it[repetitions] = state.srsState.repetitions
                    it[totalAttempts] = state.counters.totalAttempts
                    it[totalLapses] = state.counters.totalLapses
                    it[consecutiveCorrect] = state.counters.consecutiveCorrect
                    it[nextReviewAt] = state.nextReviewAt
                    it[lastReviewedAt] = state.lastReviewedAt
                }
            } else {
                LearnerVocabularyStateTable.insert {
                    it[id] = state.id.value
                    it[learnerId] = state.learnerId.value
                    it[vocabularyItemId] = state.vocabularyItemId.value
                    it[easinessFactor] = state.srsState.easinessFactor
                    it[intervalDays] = state.srsState.intervalDays
                    it[repetitions] = state.srsState.repetitions
                    it[totalAttempts] = state.counters.totalAttempts
                    it[totalLapses] = state.counters.totalLapses
                    it[consecutiveCorrect] = state.counters.consecutiveCorrect
                    it[nextReviewAt] = state.nextReviewAt
                    it[lastReviewedAt] = state.lastReviewedAt
                }
            }
        }
    }

    override suspend fun findAllForLearner(learnerId: LearnerId): List<LearnerVocabularyState> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearnerVocabularyStateTable.selectAll().where { LearnerVocabularyStateTable.learnerId eq learnerId.value }
                .map { it.toState() }
        }

    private fun ResultRow.toState() = LearnerVocabularyState(
        id = LearnerVocabularyStateId(this[LearnerVocabularyStateTable.id]),
        learnerId = LearnerId(this[LearnerVocabularyStateTable.learnerId]),
        vocabularyItemId = VocabularyItemId(this[LearnerVocabularyStateTable.vocabularyItemId]),
        srsState = SrsState(
            easinessFactor = this[LearnerVocabularyStateTable.easinessFactor],
            intervalDays = this[LearnerVocabularyStateTable.intervalDays],
            repetitions = this[LearnerVocabularyStateTable.repetitions],
        ),
        counters = MasteryCounters(
            totalAttempts = this[LearnerVocabularyStateTable.totalAttempts],
            totalLapses = this[LearnerVocabularyStateTable.totalLapses],
            consecutiveCorrect = this[LearnerVocabularyStateTable.consecutiveCorrect],
        ),
        nextReviewAt = this[LearnerVocabularyStateTable.nextReviewAt],
        lastReviewedAt = this[LearnerVocabularyStateTable.lastReviewedAt],
    )
}
