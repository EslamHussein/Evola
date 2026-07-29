package evola.vocabulary.application

import evola.core.application.Query
import evola.core.application.UseCase
import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.vocabulary.domain.VocabularyItem

data class GetDueReviewsQuery(val learnerId: LearnerId, val limit: Int = 5) : Query<List<DueReviewItem>>

data class DueReviewItem(
    val learnerVocabularyStateId: LearnerVocabularyStateId,
    val vocabularyItem: VocabularyItem,
)

class GetDueReviewsHandler(
    private val stateRepository: LearnerVocabularyStateRepository,
    private val vocabularyItemRepository: VocabularyItemRepository,
) : UseCase<GetDueReviewsQuery, List<DueReviewItem>> {

    override suspend fun handle(input: GetDueReviewsQuery): List<DueReviewItem> {
        val dueStates = stateRepository.findDueForLearner(input.learnerId, input.limit)
        return dueStates.mapNotNull { state ->
            vocabularyItemRepository.findById(state.vocabularyItemId)?.let { item ->
                DueReviewItem(state.id, item)
            }
        }
    }
}
