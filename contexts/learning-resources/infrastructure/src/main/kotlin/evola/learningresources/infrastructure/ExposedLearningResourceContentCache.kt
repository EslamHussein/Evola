package evola.learningresources.infrastructure

import evola.core.kernel.LearningResourceId
import evola.integrations.aigateway.GeneratedLearningContent
import evola.learningresources.application.LearningResourceContentCache
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/** Cache-through, mirroring exercise-generation's ExposedExerciseCache: a hit bumps times_served, no AI call. */
class ExposedLearningResourceContentCache(private val database: Database) : LearningResourceContentCache {

    override suspend fun find(resourceId: LearningResourceId, goal: String): GeneratedLearningContent? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val condition: SqlExpressionBuilder.() -> Op<Boolean> = {
                (LearningResourceGeneratedContentTable.resourceId eq resourceId.value) and
                    (LearningResourceGeneratedContentTable.goal eq goal)
            }
            val row = LearningResourceGeneratedContentTable.selectAll().where(condition).singleOrNull()
                ?: return@newSuspendedTransaction null

            LearningResourceGeneratedContentTable.update(condition) {
                it[timesServed] = row[LearningResourceGeneratedContentTable.timesServed] + 1
                it[lastServedAt] = Instant.now()
            }

            GeneratedLearningContent(
                content = row[LearningResourceGeneratedContentTable.content],
                modelUsed = row[LearningResourceGeneratedContentTable.modelUsed],
            )
        }

    override suspend fun store(resourceId: LearningResourceId, goal: String, content: GeneratedLearningContent) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearningResourceGeneratedContentTable.insert {
                it[id] = UUID.randomUUID()
                it[this.resourceId] = resourceId.value
                it[this.goal] = goal
                it[this.content] = content.content
                it[modelUsed] = content.modelUsed
                it[timesServed] = 1
                it[lastServedAt] = Instant.now()
            }
        }
    }
}
