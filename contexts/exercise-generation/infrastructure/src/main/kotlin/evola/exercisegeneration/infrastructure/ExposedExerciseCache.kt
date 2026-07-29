package evola.exercisegeneration.infrastructure

import evola.core.kernel.VocabularyItemId
import evola.exercisegeneration.application.ExerciseCache
import evola.integrations.aigateway.ExerciseType
import evola.integrations.aigateway.GeneratedExercise
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Cache-through store keyed on (vocabularyItemId, exerciseType). A hit bumps `times_served` and
 * returns the cached content with no AI call; a miss is generated once by the caller and stored
 * here forever (no TTL — an example sentence for a word doesn't go stale). This is the concrete
 * cost-reduction mechanism from ADR-0001-mvp: the same word never triggers a second LLM call.
 */
class ExposedExerciseCache(private val database: Database) : ExerciseCache {

    override suspend fun find(vocabularyItemId: VocabularyItemId, exerciseType: ExerciseType): GeneratedExercise? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = AiGeneratedContentTable.selectAll().where {
                (AiGeneratedContentTable.vocabularyItemId eq vocabularyItemId.value) and
                    (AiGeneratedContentTable.exerciseType eq exerciseType.name)
            }.singleOrNull() ?: return@newSuspendedTransaction null

            AiGeneratedContentTable.update({
                (AiGeneratedContentTable.vocabularyItemId eq vocabularyItemId.value) and
                    (AiGeneratedContentTable.exerciseType eq exerciseType.name)
            }) {
                it[timesServed] = row[AiGeneratedContentTable.timesServed] + 1
                it[lastServedAt] = Instant.now()
            }

            GeneratedExercise(
                content = row[AiGeneratedContentTable.content],
                modelUsed = row[AiGeneratedContentTable.modelUsed],
            )
        }

    override suspend fun store(vocabularyItemId: VocabularyItemId, exerciseType: ExerciseType, exercise: GeneratedExercise) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            AiGeneratedContentTable.insert {
                it[id] = UUID.randomUUID()
                it[this.vocabularyItemId] = vocabularyItemId.value
                it[this.exerciseType] = exerciseType.name
                it[content] = exercise.content
                it[modelUsed] = exercise.modelUsed
                it[timesServed] = 1
                it[lastServedAt] = Instant.now()
            }
        }
    }
}
