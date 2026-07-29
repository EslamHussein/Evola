package evola.learneridentity.infrastructure

import evola.core.kernel.LearnerId
import evola.learneridentity.domain.Learner
import evola.learneridentity.domain.LearnerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedLearnerRepository(private val database: Database) : LearnerRepository {

    override suspend fun findById(id: LearnerId): Learner? = newSuspendedTransaction(Dispatchers.IO, database) {
        LearnersTable.selectAll().where { LearnersTable.id eq id.value }
            .singleOrNull()
            ?.toLearner()
    }

    override suspend fun save(learner: Learner) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = LearnersTable.selectAll().where { LearnersTable.id eq learner.id.value }.any()
            if (!exists) {
                LearnersTable.insert {
                    it[id] = learner.id.value
                    it[createdAt] = learner.createdAt
                }
            }
        }
    }

    private fun ResultRow.toLearner() = Learner(
        id = LearnerId(this[LearnersTable.id]),
        createdAt = this[LearnersTable.createdAt],
    )
}
