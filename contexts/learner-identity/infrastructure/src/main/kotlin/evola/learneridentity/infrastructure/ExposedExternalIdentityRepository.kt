package evola.learneridentity.infrastructure

import evola.core.kernel.LearnerId
import evola.learneridentity.domain.Channel
import evola.learneridentity.domain.ExternalIdentity
import evola.learneridentity.domain.ExternalIdentityRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedExternalIdentityRepository(private val database: Database) : ExternalIdentityRepository {

    override suspend fun findByChannelAndExternalId(channel: Channel, externalId: String): ExternalIdentity? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            ExternalIdentitiesTable.selectAll().where {
                (ExternalIdentitiesTable.channel eq channel.code) and (ExternalIdentitiesTable.externalId eq externalId)
            }.singleOrNull()?.toExternalIdentity()
        }

    override suspend fun save(identity: ExternalIdentity) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            ExternalIdentitiesTable.insert {
                it[id] = identity.id
                it[learnerId] = identity.learnerId.value
                it[channel] = identity.channel.code
                it[externalId] = identity.externalId
                it[displayName] = identity.displayName
                it[createdAt] = identity.createdAt
            }
        }
    }

    private fun ResultRow.toExternalIdentity() = ExternalIdentity(
        id = this[ExternalIdentitiesTable.id],
        learnerId = LearnerId(this[ExternalIdentitiesTable.learnerId]),
        channel = Channel.fromCode(this[ExternalIdentitiesTable.channel]),
        externalId = this[ExternalIdentitiesTable.externalId],
        displayName = this[ExternalIdentitiesTable.displayName],
        createdAt = this[ExternalIdentitiesTable.createdAt],
    )
}
