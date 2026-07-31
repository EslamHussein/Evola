package evola.tutoring.infrastructure

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.VocabularyItemId
import evola.tutoring.application.LearningSessionRunRepository
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.LearningSessionRun
import evola.tutoring.domain.SessionBudgetType
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
import java.util.UUID

/** touched_vocabulary_item_ids stored as a JSON-array-of-strings TEXT column, same convention used elsewhere. */
class ExposedLearningSessionRunRepository(private val database: Database) : LearningSessionRunRepository {

    override suspend fun findById(id: LearningSessionRunId): LearningSessionRun? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            LearningSessionRunsTable.selectAll().where { LearningSessionRunsTable.id eq id.value }
                .singleOrNull()?.toRun()
        }

    override suspend fun save(run: LearningSessionRun) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            val exists = LearningSessionRunsTable.selectAll().where { LearningSessionRunsTable.id eq run.id.value }.any()
            val touchedIdsJson = Json.encodeToString(run.touchedVocabularyItemIds.map { it.value.toString() })

            if (exists) {
                LearningSessionRunsTable.update({ LearningSessionRunsTable.id eq run.id.value }) {
                    it[endedAt] = run.endedAt
                    it[questionsAsked] = run.questionsAsked
                    it[correctCount] = run.correctCount
                    it[incorrectCount] = run.incorrectCount
                    it[touchedVocabularyItemIds] = touchedIdsJson
                }
            } else {
                LearningSessionRunsTable.insert {
                    it[id] = run.id.value
                    it[learnerId] = run.learnerId.value
                    it[budgetType] = run.budgetType.name
                    it[budgetValue] = run.budgetValue
                    it[startedAt] = run.startedAt
                    it[endedAt] = run.endedAt
                    it[questionsAsked] = run.questionsAsked
                    it[correctCount] = run.correctCount
                    it[incorrectCount] = run.incorrectCount
                    it[touchedVocabularyItemIds] = touchedIdsJson
                    it[allowedKinds] = Json.encodeToString(run.allowedKinds.map { it.name })
                    it[difficultyOverride] = run.difficultyOverride?.name
                }
            }
        }
    }

    private fun ResultRow.toRun() = LearningSessionRun(
        id = LearningSessionRunId(this[LearningSessionRunsTable.id]),
        learnerId = LearnerId(this[LearningSessionRunsTable.learnerId]),
        budgetType = SessionBudgetType.valueOf(this[LearningSessionRunsTable.budgetType]),
        budgetValue = this[LearningSessionRunsTable.budgetValue],
        startedAt = this[LearningSessionRunsTable.startedAt],
        endedAt = this[LearningSessionRunsTable.endedAt],
        questionsAsked = this[LearningSessionRunsTable.questionsAsked],
        correctCount = this[LearningSessionRunsTable.correctCount],
        incorrectCount = this[LearningSessionRunsTable.incorrectCount],
        touchedVocabularyItemIds = Json.decodeFromString<List<String>>(this[LearningSessionRunsTable.touchedVocabularyItemIds])
            .map { VocabularyItemId(UUID.fromString(it)) },
        allowedKinds = Json.decodeFromString<List<String>>(this[LearningSessionRunsTable.allowedKinds]).map { ExerciseKind.valueOf(it) }.toSet(),
        difficultyOverride = this[LearningSessionRunsTable.difficultyOverride]?.let { DifficultyTier.valueOf(it) },
    )
}
