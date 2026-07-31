package evola.tutoring.infrastructure

import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.MatchPair
import evola.tutoring.application.CachedPracticeContent
import evola.tutoring.application.TutoringWordContentCache
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * Cache-through, mirroring exercise-generation's ExposedExerciseCache. PLURAL_FORM's key omits
 * the tier (a fixed grammatical fact, not difficulty-varying) — the nullable difficultyTier
 * column requires an explicit `isNull()` comparison since Postgres/Exposed `eq null` never matches.
 */
class ExposedTutoringWordContentCache(private val database: Database) : TutoringWordContentCache {

    override suspend fun find(vocabularyItemId: VocabularyItemId, kind: String, difficultyTier: String?): CachedPracticeContent? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val predicate = keyPredicate(vocabularyItemId, kind, difficultyTier)
            val row = TutoringWordContentTable.selectAll().where(predicate).singleOrNull()
                ?: return@newSuspendedTransaction null

            TutoringWordContentTable.update(predicate) {
                it[timesServed] = row[TutoringWordContentTable.timesServed] + 1
                it[lastServedAt] = Instant.now()
            }

            row.toCached()
        }

    override suspend fun store(vocabularyItemId: VocabularyItemId, kind: String, difficultyTier: String?, content: CachedPracticeContent) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            TutoringWordContentTable.insert {
                it[id] = UUID.randomUUID()
                it[this.vocabularyItemId] = vocabularyItemId.value
                it[this.kind] = kind
                it[this.difficultyTier] = difficultyTier
                it[promptText] = content.promptText
                it[correctAnswer] = content.correctAnswer
                it[hint] = content.hint
                it[explanation] = content.explanation
                it[options] = content.options?.let { opts -> Json.encodeToString(opts) }
                it[matchPairs] = content.matchPairs?.let { pairs -> Json.encodeToString(pairs) }
                it[modelUsed] = content.modelUsed
                it[timesServed] = 1
                it[lastServedAt] = Instant.now()
            }
        }
    }

    private fun keyPredicate(vocabularyItemId: VocabularyItemId, kind: String, difficultyTier: String?): SqlExpressionBuilder.() -> Op<Boolean> = {
        val tierCondition = if (difficultyTier == null) {
            TutoringWordContentTable.difficultyTier.isNull()
        } else {
            TutoringWordContentTable.difficultyTier eq difficultyTier
        }
        (TutoringWordContentTable.vocabularyItemId eq vocabularyItemId.value) and
            (TutoringWordContentTable.kind eq kind) and tierCondition
    }

    private fun ResultRow.toCached() = CachedPracticeContent(
        promptText = this[TutoringWordContentTable.promptText],
        correctAnswer = this[TutoringWordContentTable.correctAnswer],
        hint = this[TutoringWordContentTable.hint],
        explanation = this[TutoringWordContentTable.explanation],
        options = this[TutoringWordContentTable.options]?.let { Json.decodeFromString<List<String>>(it) },
        matchPairs = this[TutoringWordContentTable.matchPairs]?.let { Json.decodeFromString<List<MatchPair>>(it) },
        modelUsed = this[TutoringWordContentTable.modelUsed],
    )
}
