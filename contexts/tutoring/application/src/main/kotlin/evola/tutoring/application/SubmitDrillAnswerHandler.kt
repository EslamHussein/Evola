package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DialogueTurnId
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.EvaluateFreeformAnswerRequest
import evola.integrations.aigateway.MatchPair
import evola.integrations.aigateway.PracticeExerciseKind
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.FollowUpSelector
import evola.tutoring.domain.TurnRole
import evola.tutoring.domain.WordMatchingGrader
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.application.ReviewHistoryRepository
import evola.vocabulary.application.VocabularyItemRepository
import evola.vocabulary.domain.ReviewGrader
import evola.vocabulary.domain.ReviewHistoryEntry
import evola.vocabulary.domain.VocabularyItem
import evola.vocabulary.domain.VocabularyReviewService
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SubmitDrillAnswerCommand(
    val learnerId: LearnerId,
    val sessionId: TutoringSessionId,
    val rawAnswer: String,
) : Command<DomainResult<SubmitDrillAnswerResult>>

data class SubmitDrillAnswerResult(
    val wasCorrect: Boolean,
    val feedback: String,
    val followUpPrompt: String?,
    val sessionCompleted: Boolean,
    /** Always a free-text kind today (PLURAL_FORM/SENTENCE_CREATION) — null when there's no follow-up. */
    val followUpExerciseKind: ExerciseKind? = null,
)

/**
 * Grades deterministically (ReviewGrader, zero LLM cost) for templated/generated exercise kinds;
 * only genuinely open-ended kinds (SENTENCE_CREATION) go through an AI evaluation call — the
 * direct cost-principle application from the plan.
 */
class SubmitDrillAnswerHandler(
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
    private val stateRepository: LearnerVocabularyStateRepository,
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val reviewHistoryRepository: ReviewHistoryRepository,
    private val contentProvider: GetOrGeneratePracticeExerciseHandler,
    private val aiTutorPort: AiTutorPort,
) : UseCase<SubmitDrillAnswerCommand, DomainResult<SubmitDrillAnswerResult>> {

    override suspend fun handle(input: SubmitDrillAnswerCommand): DomainResult<SubmitDrillAnswerResult> {
        val session = sessionRepository.findById(input.sessionId)
            ?: return DomainResult.Err(DomainError.NotFound("No such tutoring session"))
        if (session.learnerId != input.learnerId) {
            return DomainResult.Err(DomainError.Conflict("Session does not belong to the requesting learner"))
        }
        val vocabularyItemId = session.focusVocabularyItemId
            ?: return DomainResult.Err(DomainError.ValidationFailed("Session has no focus vocabulary item"))
        val item = vocabularyItemRepository.findById(vocabularyItemId)
            ?: return DomainResult.Err(DomainError.NotFound("Vocabulary item no longer exists"))

        val turns = turnRepository.findBySession(session.id)
        val pendingPrompt = turns.lastOrNull { it.role == TurnRole.PROMPT || it.role == TurnRole.FOLLOW_UP_PROMPT }
            ?: return DomainResult.Err(DomainError.ValidationFailed("No pending question for this session"))

        val state = stateRepository.findAllForLearner(input.learnerId)
            .firstOrNull { it.vocabularyItemId == vocabularyItemId }
            ?: return DomainResult.Err(DomainError.NotFound("No vocabulary state for this learner/item"))

        val quality: Int
        val wasCorrect: Boolean
        val feedbackText: String

        val exerciseKind = pendingPrompt.exerciseKind
        if (exerciseKind == ExerciseKind.SENTENCE_CREATION) {
            val evaluation = aiTutorPort.evaluateFreeformAnswer(
                EvaluateFreeformAnswerRequest(
                    exerciseKind = exerciseKind.name,
                    prompt = pendingPrompt.content,
                    targetWordOrTopic = item.germanWord,
                    learnerAnswer = input.rawAnswer,
                    cefrLevel = item.cefrLevel.code,
                ),
            )
            quality = evaluation.qualityScore
            wasCorrect = evaluation.isCorrect
            feedbackText = evaluation.feedback
        } else if (exerciseKind == ExerciseKind.WORD_MATCHING) {
            val correctPairs = decodeMatchPairs(pendingPrompt.correctAnswer)
            val submittedPairs = decodeMatchPairs(input.rawAnswer)
            quality = WordMatchingGrader.grade(
                correctPairs.map { it.left to it.right },
                submittedPairs.map { it.left to it.right },
            )
            wasCorrect = quality >= 3
            feedbackText = if (wasCorrect) {
                "Richtig!"
            } else {
                "Nicht ganz — richtige Zuordnung: " + correctPairs.joinToString(", ") { "${it.left} → ${it.right}" }
            }
        } else {
            quality = ReviewGrader.grade(pendingPrompt.correctAnswer.orEmpty(), input.rawAnswer)
            wasCorrect = quality >= 3
            feedbackText = if (wasCorrect) {
                "Richtig!"
            } else {
                "Nicht ganz — richtige Antwort: ${pendingPrompt.correctAnswer}" +
                    (pendingPrompt.explanation?.let { " ($it)" } ?: "")
            }
        }

        val now = Instant.now()
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, pendingPrompt.turnIndex + 1, TurnRole.LEARNER_ANSWER,
                pendingPrompt.exerciseKind, input.rawAnswer, null, null, wasCorrect, now,
            ),
        )
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, pendingPrompt.turnIndex + 2, TurnRole.FEEDBACK,
                pendingPrompt.exerciseKind, feedbackText, null, null, null, now,
            ),
        )

        val outcome = VocabularyReviewService.applyQuality(state.srsState, state.counters, quality)
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
                wasCorrect = wasCorrect,
                qualityScore = quality,
                easinessFactorBefore = state.srsState.easinessFactor,
                easinessFactorAfter = outcome.updatedSrsState.easinessFactor,
                intervalDaysBefore = state.srsState.intervalDays,
                intervalDaysAfter = outcome.updatedSrsState.intervalDays,
            ),
        )

        // A follow-up is only offered once, after the session's very first prompt — never after
        // the follow-up's own answer, which always ends the session.
        val followUp: Pair<String, ExerciseKind>? = if (pendingPrompt.turnIndex == 0) {
            val tier = tierFor(pendingPrompt.exerciseKind)
            val followUpKind = FollowUpSelector.selectFollowUp(tier, item.partOfSpeech)
            followUpKind?.let { kind ->
                val (promptText, correctAnswer) = buildFollowUpContent(kind, item, tier)
                turnRepository.append(
                    DialogueTurn(
                        DialogueTurnId.new(), session.id, pendingPrompt.turnIndex + 3, TurnRole.FOLLOW_UP_PROMPT,
                        kind, promptText, correctAnswer, null, null, now,
                    ),
                )
                promptText to kind
            }
        } else {
            null
        }

        if (followUp == null) {
            sessionRepository.save(session.complete(now))
        }

        return DomainResult.Ok(
            SubmitDrillAnswerResult(
                wasCorrect = wasCorrect,
                feedback = feedbackText,
                followUpPrompt = followUp?.first,
                sessionCompleted = followUp == null,
                followUpExerciseKind = followUp?.second,
            ),
        )
    }

    private fun decodeMatchPairs(raw: String?): List<MatchPair> =
        runCatching { Json.decodeFromString<List<MatchPair>>(raw.orEmpty()) }.getOrDefault(emptyList())

    private fun tierFor(kind: ExerciseKind?): DifficultyTier = when (kind) {
        ExerciseKind.TRANSLATE, ExerciseKind.MULTIPLE_CHOICE -> DifficultyTier.BEGINNER
        ExerciseKind.CLOZE_SENTENCE, ExerciseKind.SCENARIO_CLOZE, ExerciseKind.SENTENCE_CREATION -> DifficultyTier.ADVANCED
        else -> DifficultyTier.BEGINNER
    }

    private suspend fun buildFollowUpContent(kind: ExerciseKind, item: VocabularyItem, tier: DifficultyTier): Pair<String, String?> =
        when (kind) {
            ExerciseKind.TRANSLATE -> {
                val generated = contentProvider.forWord(
                    vocabularyItemId = item.id,
                    germanWord = item.germanWord,
                    englishTranslation = item.englishTranslation,
                    cefrLevel = item.cefrLevel.code,
                    kind = PracticeExerciseKind.PLURAL_FORM,
                    tier = tier,
                )
                generated.promptText to generated.correctAnswer
            }

            ExerciseKind.SENTENCE_CREATION ->
                "Create a sentence in German using the word \"${item.germanWord}\"." to null

            else -> "Try again." to null
        }
}
