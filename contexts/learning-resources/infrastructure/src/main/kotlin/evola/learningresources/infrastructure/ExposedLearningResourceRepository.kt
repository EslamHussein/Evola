package evola.learningresources.infrastructure

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningResourceId
import evola.learningresources.application.LearningResourceRepository
import evola.learningresources.domain.LearningResource
import evola.learningresources.domain.ResourceOverview
import evola.learningresources.domain.ResourceStatus
import evola.learningresources.domain.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

/** topics stored as a JSON-array-of-strings TEXT column, same convention used across this schema. */
class ExposedLearningResourceRepository(private val database: Database) : LearningResourceRepository {

    override suspend fun findById(id: LearningResourceId): LearningResource? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearningResourcesTable.selectAll().where { LearningResourcesTable.id eq id.value }
                .singleOrNull()?.toResource()
        }

    override suspend fun save(resource: LearningResource) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = LearningResourcesTable.selectAll().where { LearningResourcesTable.id eq resource.id.value }.any()

            if (exists) {
                LearningResourcesTable.update({ LearningResourcesTable.id eq resource.id.value }) {
                    it[status] = resource.status.name
                    it[language] = resource.overview?.language
                    it[cefrLevel] = resource.overview?.cefrLevel
                    it[topics] = resource.overview?.topics?.let { t -> Json.encodeToString(t) }
                    it[overviewSummary] = resource.overview?.summary
                    it[overviewModelUsed] = resource.overview?.modelUsed
                    it[analysisTruncated] = resource.overview?.wasTruncated ?: false
                }
            } else {
                LearningResourcesTable.insert {
                    it[id] = resource.id.value
                    it[learnerId] = resource.learnerId.value
                    it[title] = resource.title
                    it[sourceType] = resource.sourceType.name
                    it[storagePath] = resource.storagePath
                    it[extractedText] = resource.extractedText
                    it[status] = resource.status.name
                    it[analysisTruncated] = false
                    it[createdAt] = resource.createdAt
                }
            }
        }
    }

    private fun ResultRow.toResource(): LearningResource {
        val overview = this[LearningResourcesTable.language]?.let { language ->
            ResourceOverview(
                language = language,
                cefrLevel = this[LearningResourcesTable.cefrLevel] ?: "UNKNOWN",
                topics = this[LearningResourcesTable.topics]?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList(),
                summary = this[LearningResourcesTable.overviewSummary] ?: "",
                modelUsed = this[LearningResourcesTable.overviewModelUsed] ?: "",
                wasTruncated = this[LearningResourcesTable.analysisTruncated],
            )
        }
        return LearningResource(
            id = LearningResourceId(this[LearningResourcesTable.id]),
            learnerId = LearnerId(this[LearningResourcesTable.learnerId]),
            title = this[LearningResourcesTable.title],
            sourceType = SourceType.valueOf(this[LearningResourcesTable.sourceType]),
            storagePath = this[LearningResourcesTable.storagePath],
            extractedText = this[LearningResourcesTable.extractedText],
            status = ResourceStatus.valueOf(this[LearningResourcesTable.status]),
            overview = overview,
            createdAt = this[LearningResourcesTable.createdAt],
        )
    }
}
