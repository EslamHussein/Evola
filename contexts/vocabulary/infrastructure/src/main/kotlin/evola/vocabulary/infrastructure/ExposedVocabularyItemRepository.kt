package evola.vocabulary.infrastructure

import evola.core.kernel.CefrLevel
import evola.core.kernel.LearnerId
import evola.core.kernel.VocabularyItemId
import evola.vocabulary.application.VocabularyItemRepository
import evola.vocabulary.domain.VocabularyItem
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

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

            VocabularyItemsTable.selectAll()
                .where { VocabularyItemsTable.id notInSubQuery alreadySeen }
                .orderBy(VocabularyItemsTable.createdAt)
                .limit(1)
                .singleOrNull()
                ?.toVocabularyItem()
        }

    private fun ResultRow.toVocabularyItem() = VocabularyItem(
        id = VocabularyItemId(this[VocabularyItemsTable.id]),
        germanWord = this[VocabularyItemsTable.germanWord],
        englishTranslation = this[VocabularyItemsTable.englishTranslation],
        cefrLevel = CefrLevel.fromCode(this[VocabularyItemsTable.cefrLevel]),
        partOfSpeech = this[VocabularyItemsTable.partOfSpeech],
    )
}
