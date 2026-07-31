package evola.tutoring.infrastructure

import evola.tutoring.application.CachedPracticeContent
import evola.tutoring.application.TutoringGrammarContentCache
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

class ExposedTutoringGrammarContentCache(private val database: Database) : TutoringGrammarContentCache {

    override suspend fun find(grammarTopic: String, difficultyTier: String): CachedPracticeContent? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val predicate = keyPredicate(grammarTopic, difficultyTier)
            val row = TutoringGrammarContentTable.selectAll().where(predicate).singleOrNull()
                ?: return@newSuspendedTransaction null

            TutoringGrammarContentTable.update(predicate) {
                it[timesServed] = row[TutoringGrammarContentTable.timesServed] + 1
                it[lastServedAt] = Instant.now()
            }

            row.toCached()
        }

    override suspend fun store(grammarTopic: String, difficultyTier: String, content: CachedPracticeContent) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            TutoringGrammarContentTable.insert {
                it[id] = UUID.randomUUID()
                it[this.grammarTopic] = grammarTopic
                it[this.difficultyTier] = difficultyTier
                it[promptText] = content.promptText
                it[correctAnswer] = content.correctAnswer
                it[explanation] = content.explanation
                it[options] = content.options?.let { opts -> Json.encodeToString(opts) }
                it[modelUsed] = content.modelUsed
                it[timesServed] = 1
                it[lastServedAt] = Instant.now()
            }
        }
    }

    private fun keyPredicate(grammarTopic: String, difficultyTier: String): SqlExpressionBuilder.() -> Op<Boolean> = {
        (TutoringGrammarContentTable.grammarTopic eq grammarTopic) and (TutoringGrammarContentTable.difficultyTier eq difficultyTier)
    }

    private fun ResultRow.toCached() = CachedPracticeContent(
        promptText = this[TutoringGrammarContentTable.promptText],
        correctAnswer = this[TutoringGrammarContentTable.correctAnswer],
        hint = null,
        explanation = this[TutoringGrammarContentTable.explanation],
        options = this[TutoringGrammarContentTable.options]?.let { Json.decodeFromString<List<String>>(it) },
        modelUsed = this[TutoringGrammarContentTable.modelUsed],
    )
}
