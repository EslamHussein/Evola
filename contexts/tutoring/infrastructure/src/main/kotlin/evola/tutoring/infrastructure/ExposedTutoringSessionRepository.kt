package evola.tutoring.infrastructure

import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.core.kernel.VocabularyItemId
import evola.tutoring.application.TutoringSessionRepository
import evola.tutoring.domain.LearningMode
import evola.tutoring.domain.SessionStatus
import evola.tutoring.domain.TutoringSession
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class ExposedTutoringSessionRepository(private val database: Database) : TutoringSessionRepository {

    override suspend fun findById(id: TutoringSessionId): TutoringSession? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            TutoringSessionsTable.selectAll().where { TutoringSessionsTable.id eq id.value }
                .singleOrNull()?.toSession()
        }

    override suspend fun save(session: TutoringSession) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = TutoringSessionsTable.selectAll().where { TutoringSessionsTable.id eq session.id.value }.any()

            if (exists) {
                TutoringSessionsTable.update({ TutoringSessionsTable.id eq session.id.value }) {
                    it[status] = session.status.name
                    it[completedAt] = session.completedAt
                }
            } else {
                TutoringSessionsTable.insert {
                    it[id] = session.id.value
                    it[learnerId] = session.learnerId.value
                    it[mode] = session.mode.name
                    it[focusVocabularyItemId] = session.focusVocabularyItemId?.value
                    it[focusGrammarTopic] = session.focusGrammarTopic
                    it[status] = session.status.name
                    it[startedAt] = session.startedAt
                    it[completedAt] = session.completedAt
                }
            }
        }
    }

    private fun ResultRow.toSession() = TutoringSession(
        id = TutoringSessionId(this[TutoringSessionsTable.id]),
        learnerId = LearnerId(this[TutoringSessionsTable.learnerId]),
        mode = LearningMode.valueOf(this[TutoringSessionsTable.mode]),
        focusVocabularyItemId = this[TutoringSessionsTable.focusVocabularyItemId]?.let { VocabularyItemId(it) },
        focusGrammarTopic = this[TutoringSessionsTable.focusGrammarTopic],
        status = SessionStatus.valueOf(this[TutoringSessionsTable.status]),
        startedAt = this[TutoringSessionsTable.startedAt],
        completedAt = this[TutoringSessionsTable.completedAt],
    )
}
