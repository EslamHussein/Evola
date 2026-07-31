package evola.vocabulary.infrastructure

import evola.core.kernel.CefrLevel
import evola.core.kernel.LearnerId
import evola.core.kernel.VocabularyItemId
import evola.vocabulary.application.VocabularyItemRepository
import evola.vocabulary.domain.VocabularyItem
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
import java.util.UUID

class ExposedVocabularyItemRepository(private val database: Database) : VocabularyItemRepository {

    override suspend fun findById(id: VocabularyItemId): VocabularyItem? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyItemsTable.selectAll().where { VocabularyItemsTable.id eq id.value }
                .singleOrNull()?.toVocabularyItem()
        }

    override suspend fun findNextUnseenFor(learnerId: LearnerId): VocabularyItem? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val alreadySeen = LearnerVocabularyStateTable
                .select(LearnerVocabularyStateTable.vocabularyItemId)
                .where { LearnerVocabularyStateTable.learnerId eq learnerId.value }

            // Scoped to the shared seed pool only — another learner's private custom words must
            // never surface here; custom words get their LearnerVocabularyState created directly
            // at extraction time instead of being "discovered" through this query.
            VocabularyItemsTable.selectAll()
                .where {
                    (VocabularyItemsTable.ownerLearnerId.isNull()) and
                        (VocabularyItemsTable.id notInSubQuery alreadySeen)
                }
                .orderBy(VocabularyItemsTable.createdAt)
                .limit(1)
                .singleOrNull()
                ?.toVocabularyItem()
        }

    override suspend fun save(item: VocabularyItem) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyItemsTable.insert {
                it[id] = item.id.value
                it[germanWord] = item.germanWord
                it[englishTranslation] = item.englishTranslation
                it[cefrLevel] = item.cefrLevel.code
                it[partOfSpeech] = item.partOfSpeech
                it[article] = item.article
                it[pluralForm] = item.pluralForm
                it[exampleSentence] = item.exampleSentence
                it[topic] = item.topic
                it[synonyms] = if (item.synonyms.isEmpty()) null else Json.encodeToString(item.synonyms)
                it[relatedWords] = if (item.relatedWords.isEmpty()) null else Json.encodeToString(item.relatedWords)
                it[ownerLearnerId] = item.ownerLearnerId?.value
                it[createdAt] = java.time.Instant.now()
            }
        }
    }

    override suspend fun findByOwnerAndWord(learnerId: LearnerId, germanWord: String): VocabularyItem? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            VocabularyItemsTable.selectAll().where {
                (VocabularyItemsTable.ownerLearnerId eq learnerId.value) and
                    (VocabularyItemsTable.germanWord eq germanWord)
            }.singleOrNull()?.toVocabularyItem()
        }

    private fun ResultRow.toVocabularyItem() = VocabularyItem(
        id = VocabularyItemId(this[VocabularyItemsTable.id]),
        germanWord = this[VocabularyItemsTable.germanWord],
        englishTranslation = this[VocabularyItemsTable.englishTranslation],
        cefrLevel = CefrLevel.fromCode(this[VocabularyItemsTable.cefrLevel]),
        partOfSpeech = this[VocabularyItemsTable.partOfSpeech],
        article = this[VocabularyItemsTable.article],
        pluralForm = this[VocabularyItemsTable.pluralForm],
        exampleSentence = this[VocabularyItemsTable.exampleSentence],
        topic = this[VocabularyItemsTable.topic],
        synonyms = this[VocabularyItemsTable.synonyms]?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList(),
        relatedWords = this[VocabularyItemsTable.relatedWords]?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList(),
        ownerLearnerId = this[VocabularyItemsTable.ownerLearnerId]?.let { LearnerId(it) },
    )
}
