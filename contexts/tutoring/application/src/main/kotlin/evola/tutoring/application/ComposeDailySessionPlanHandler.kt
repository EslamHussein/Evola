package evola.tutoring.application

import evola.core.application.Query
import evola.core.application.UseCase
import evola.core.kernel.DailySessionPlanId
import evola.core.kernel.LearnerId
import evola.tutoring.domain.DailySessionPlan
import evola.tutoring.domain.SpeakingScenarioCatalog
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.domain.MasteryStatus
import java.time.Instant
import java.time.LocalDate

data class ComposeDailySessionPlanQuery(val learnerId: LearnerId) : Query<DailySessionPlan>

/**
 * Composed entirely from queryable data — no LLM call to decide the plan, only to render content
 * once the learner actually starts a section. Idempotent per (learner, day): re-running returns
 * the same plan rather than regenerating it.
 */
class ComposeDailySessionPlanHandler(
    private val planRepository: DailySessionPlanRepository,
    private val stateRepository: LearnerVocabularyStateRepository,
    private val turnRepository: DialogueTurnRepository,
) : UseCase<ComposeDailySessionPlanQuery, DailySessionPlan> {

    override suspend fun handle(input: ComposeDailySessionPlanQuery): DailySessionPlan {
        val today = LocalDate.now()
        planRepository.findByLearnerAndDate(input.learnerId, today)?.let { return it }

        val dueCount = stateRepository.findDueForLearner(input.learnerId, limit = 1000).size
        val allStates = stateRepository.findAllForLearner(input.learnerId)
        val weakIds = allStates.filter { it.status == MasteryStatus.NEEDS_PRACTICE }.map { it.vocabularyItemId }.take(5)
        val grammarTopic = turnRepository.mostFrequentWrongGrammarTopic(input.learnerId)
        val scenario = SpeakingScenarioCatalog.pickFor(today.toEpochDay().toInt() + input.learnerId.value.hashCode())

        val plan = DailySessionPlan(
            id = DailySessionPlanId.new(),
            learnerId = input.learnerId,
            planDate = today,
            dueReviewCount = dueCount,
            weakVocabularyItemIds = weakIds,
            grammarFocusTopic = grammarTopic,
            speakingScenarioTitle = scenario.title,
            createdAt = Instant.now(),
        )
        planRepository.save(plan)
        return plan
    }
}
