package evola.vocabulary.domain

import evola.core.kernel.LearnerVocabularyStateId
import java.time.Instant
import java.util.UUID

data class ReviewHistoryEntry(
    val id: UUID,
    val learnerVocabularyStateId: LearnerVocabularyStateId,
    val reviewedAt: Instant,
    val learnerAnswer: String,
    val wasCorrect: Boolean,
    val qualityScore: Int,
    val easinessFactorBefore: Double,
    val easinessFactorAfter: Double,
    val intervalDaysBefore: Int,
    val intervalDaysAfter: Int,
)
