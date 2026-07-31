package evola.tutoring.infrastructure

import evola.core.kernel.DailySessionPlanId
import evola.core.kernel.LearnerId
import evola.core.kernel.VocabularyItemId
import evola.tutoring.application.DailySessionPlanRepository
import evola.tutoring.domain.DailySessionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.util.UUID

/** weak_vocabulary_item_ids stored as a JSON-array-of-strings TEXT column, same convention used elsewhere. */
class ExposedDailySessionPlanRepository(private val database: Database) : DailySessionPlanRepository {

    override suspend fun findByLearnerAndDate(learnerId: LearnerId, date: LocalDate): DailySessionPlan? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            DailySessionPlansTable.selectAll().where {
                (DailySessionPlansTable.learnerId eq learnerId.value) and (DailySessionPlansTable.planDate eq date)
            }.singleOrNull()?.toPlan()
        }

    override suspend fun save(plan: DailySessionPlan) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            DailySessionPlansTable.insert {
                it[id] = plan.id.value
                it[learnerId] = plan.learnerId.value
                it[planDate] = plan.planDate
                it[dueReviewCount] = plan.dueReviewCount
                it[weakVocabularyItemIds] = Json.encodeToString(plan.weakVocabularyItemIds.map { item -> item.value.toString() })
                it[grammarFocusTopic] = plan.grammarFocusTopic
                it[speakingScenarioTitle] = plan.speakingScenarioTitle
                it[createdAt] = plan.createdAt
            }
        }
    }

    private fun ResultRow.toPlan() = DailySessionPlan(
        id = DailySessionPlanId(this[DailySessionPlansTable.id]),
        learnerId = LearnerId(this[DailySessionPlansTable.learnerId]),
        planDate = this[DailySessionPlansTable.planDate],
        dueReviewCount = this[DailySessionPlansTable.dueReviewCount],
        weakVocabularyItemIds = Json.decodeFromString<List<String>>(this[DailySessionPlansTable.weakVocabularyItemIds])
            .map { VocabularyItemId(UUID.fromString(it)) },
        grammarFocusTopic = this[DailySessionPlansTable.grammarFocusTopic],
        speakingScenarioTitle = this[DailySessionPlansTable.speakingScenarioTitle],
        createdAt = this[DailySessionPlansTable.createdAt],
    )
}
