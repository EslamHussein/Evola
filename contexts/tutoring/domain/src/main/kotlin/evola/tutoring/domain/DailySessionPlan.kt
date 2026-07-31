package evola.tutoring.domain

import evola.core.kernel.DailySessionPlanId
import evola.core.kernel.LearnerId
import evola.core.kernel.VocabularyItemId
import java.time.Instant
import java.time.LocalDate

data class DailySessionPlan(
    val id: DailySessionPlanId,
    val learnerId: LearnerId,
    val planDate: LocalDate,
    val dueReviewCount: Int,
    val weakVocabularyItemIds: List<VocabularyItemId>,
    val grammarFocusTopic: String?,
    val speakingScenarioTitle: String?,
    val createdAt: Instant,
)
