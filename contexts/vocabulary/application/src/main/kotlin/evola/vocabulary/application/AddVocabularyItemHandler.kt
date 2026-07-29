package evola.vocabulary.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.vocabulary.domain.LearnerVocabularyState
import evola.vocabulary.domain.VocabularyItem

data class AddVocabularyItemCommand(val learnerId: LearnerId) : Command<DomainResult<AddVocabularyItemResult>>

data class AddVocabularyItemResult(
    val vocabularyItem: VocabularyItem,
    val learnerVocabularyStateId: LearnerVocabularyStateId,
)

class AddVocabularyItemHandler(
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val stateRepository: LearnerVocabularyStateRepository,
) : UseCase<AddVocabularyItemCommand, DomainResult<AddVocabularyItemResult>> {

    override suspend fun handle(input: AddVocabularyItemCommand): DomainResult<AddVocabularyItemResult> {
        val item = vocabularyItemRepository.findNextUnseenFor(input.learnerId)
            ?: return DomainResult.Err(DomainError.NotFound("No more new vocabulary items available for this learner"))

        val state = LearnerVocabularyState.newFor(
            id = LearnerVocabularyStateId.new(),
            learnerId = input.learnerId,
            vocabularyItemId = item.id,
        )
        stateRepository.save(state)

        return DomainResult.Ok(AddVocabularyItemResult(item, state.id))
    }
}
