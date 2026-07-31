package evola.vocabulary.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.vocabulary.domain.ReviewHistoryEntry
import evola.vocabulary.domain.VocabularyReviewService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SubmitReviewAnswerCommand(
    val learnerId: LearnerId,
    val learnerVocabularyStateId: LearnerVocabularyStateId,
    val rawAnswer: String,
) : Command<DomainResult<SubmitReviewAnswerResult>>

data class SubmitReviewAnswerResult(
    val wasCorrect: Boolean,
    val correctAnswer: String,
)

class SubmitReviewAnswerHandler(
    private val stateRepository: LearnerVocabularyStateRepository,
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val reviewHistoryRepository: ReviewHistoryRepository,
) : UseCase<SubmitReviewAnswerCommand, DomainResult<SubmitReviewAnswerResult>> {

    override suspend fun handle(input: SubmitReviewAnswerCommand): DomainResult<SubmitReviewAnswerResult> {
        val state = stateRepository.findById(input.learnerVocabularyStateId)
            ?: return DomainResult.Err(DomainError.NotFound("No such review state"))

        if (state.learnerId != input.learnerId) {
            return DomainResult.Err(DomainError.Conflict("This review does not belong to the requesting learner"))
        }

        val item = vocabularyItemRepository.findById(state.vocabularyItemId)
            ?: return DomainResult.Err(DomainError.NotFound("Vocabulary item no longer exists"))

        val outcome = VocabularyReviewService.review(
            currentSrsState = state.srsState,
            currentCounters = state.counters,
            expectedAnswer = item.englishTranslation,
            givenAnswer = input.rawAnswer,
        )

        val now = Instant.now()
        val updatedState = state.copy(
            srsState = outcome.updatedSrsState,
            counters = outcome.updatedCounters,
            lastReviewedAt = now,
            nextReviewAt = now.plus(outcome.updatedSrsState.intervalDays.toLong(), ChronoUnit.DAYS),
        )
        stateRepository.save(updatedState)

        reviewHistoryRepository.record(
            ReviewHistoryEntry(
                id = UUID.randomUUID(),
                learnerVocabularyStateId = state.id,
                reviewedAt = now,
                learnerAnswer = input.rawAnswer,
                wasCorrect = outcome.wasCorrect,
                qualityScore = outcome.qualityScore,
                easinessFactorBefore = state.srsState.easinessFactor,
                easinessFactorAfter = outcome.updatedSrsState.easinessFactor,
                intervalDaysBefore = state.srsState.intervalDays,
                intervalDaysAfter = outcome.updatedSrsState.intervalDays,
            ),
        )

        return DomainResult.Ok(SubmitReviewAnswerResult(outcome.wasCorrect, item.englishTranslation))
    }
}
