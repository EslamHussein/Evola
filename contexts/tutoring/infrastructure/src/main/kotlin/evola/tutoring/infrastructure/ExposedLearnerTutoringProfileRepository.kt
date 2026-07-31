package evola.tutoring.infrastructure

import evola.core.kernel.LearnerId
import evola.tutoring.application.LearnerTutoringProfileRepository
import evola.tutoring.domain.LearningMode
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedLearnerTutoringProfileRepository(private val database: Database) : LearnerTutoringProfileRepository {

    override suspend fun getActiveMode(learnerId: LearnerId): LearningMode? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearnerTutoringProfileTable.selectAll().where { LearnerTutoringProfileTable.learnerId eq learnerId.value }
                .singleOrNull()?.let { LearningMode.valueOf(it[LearnerTutoringProfileTable.activeLearningMode]) }
        }

    override suspend fun setActiveMode(learnerId: LearnerId, mode: LearningMode) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = LearnerTutoringProfileTable.selectAll()
                .where { LearnerTutoringProfileTable.learnerId eq learnerId.value }.any()

            if (exists) {
                LearnerTutoringProfileTable.update({ LearnerTutoringProfileTable.learnerId eq learnerId.value }) {
                    it[activeLearningMode] = mode.name
                    it[updatedAt] = Instant.now()
                }
            } else {
                LearnerTutoringProfileTable.insert {
                    it[this.learnerId] = learnerId.value
                    it[activeLearningMode] = mode.name
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }
}
