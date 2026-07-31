package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.MatchPair
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.LearningSessionRun
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.domain.MasteryStatus
import java.time.Duration
import java.time.Instant

data class SubmitSessionAnswerCommand(
    val learnerId: LearnerId,
    val sessionRunId: LearningSessionRunId,
    val tutoringSessionId: TutoringSessionId,
    val rawAnswer: String,
) : Command<DomainResult<SubmitSessionAnswerResult>>

data class SubmitSessionAnswerResult(
    val wasCorrect: Boolean,
    val feedback: String,
    val nextPrompt: String?,
    val nextTutoringSessionId: TutoringSessionId?,
    val sessionCompleted: Boolean,
    val summary: SessionSummary?,
    val nextExerciseKind: ExerciseKind? = null,
    val nextOptions: List<String>? = null,
    val nextMatchPairs: List<MatchPair>? = null,
)

data class SessionSummary(
    val durationMinutes: Long,
    val questionsAsked: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val accuracyPercent: Int,
    val wordsMastered: Int,
    val wordsNeedingPractice: Int,
)

/**
 * Delegates grading to SubmitDrillAnswerHandler (Milestone 3's unchanged logic — SM-2, deterministic
 * or AI grading, mastery ladder all stay in exactly one place) and only adds budget-tracking and
 * summarization on top: continue to the next due word, or close out the session with a summary.
 */
class SubmitSessionAnswerHandler(
    private val sessionRunRepository: LearningSessionRunRepository,
    private val tutoringSessionRepository: TutoringSessionRepository,
    private val submitDrillAnswerHandler: SubmitDrillAnswerHandler,
    private val startVocabularyDrillHandler: StartVocabularyDrillHandler,
    private val learnerVocabularyStateRepository: LearnerVocabularyStateRepository,
) : UseCase<SubmitSessionAnswerCommand, DomainResult<SubmitSessionAnswerResult>> {

    override suspend fun handle(input: SubmitSessionAnswerCommand): DomainResult<SubmitSessionAnswerResult> {
        var run = sessionRunRepository.findById(input.sessionRunId)
            ?: return DomainResult.Err(DomainError.NotFound("No such learning session"))
        if (run.learnerId != input.learnerId) {
            return DomainResult.Err(DomainError.Conflict("Session does not belong to the requesting learner"))
        }

        val tutoringSession = tutoringSessionRepository.findById(input.tutoringSessionId)
            ?: return DomainResult.Err(DomainError.NotFound("No such tutoring session"))
        val vocabularyItemId = tutoringSession.focusVocabularyItemId
            ?: return DomainResult.Err(DomainError.ValidationFailed("Session has no focus vocabulary item"))

        val drillResult = submitDrillAnswerHandler.handle(
            SubmitDrillAnswerCommand(input.learnerId, input.tutoringSessionId, input.rawAnswer),
        )
        if (drillResult is DomainResult.Err) return drillResult
        val drillOutcome = (drillResult as DomainResult.Ok).value

        // A drill with a follow-up turn (e.g. plural form) doesn't count as a new question in the
        // session budget until it's fully answered — only tally once the drill itself is complete.
        if (!drillOutcome.sessionCompleted) {
            return DomainResult.Ok(
                SubmitSessionAnswerResult(
                    wasCorrect = drillOutcome.wasCorrect,
                    feedback = drillOutcome.feedback,
                    nextPrompt = drillOutcome.followUpPrompt,
                    nextTutoringSessionId = input.tutoringSessionId,
                    sessionCompleted = false,
                    summary = null,
                    nextExerciseKind = drillOutcome.followUpExerciseKind,
                ),
            )
        }

        run = run.recordAnswer(drillOutcome.wasCorrect, vocabularyItemId)
        sessionRunRepository.save(run)

        if (!run.isBudgetExhausted()) {
            val nextDrill = startVocabularyDrillHandler.handle(
                StartVocabularyDrillCommand(input.learnerId, run.allowedKinds.ifEmpty { null }, run.difficultyOverride),
            )
            return when (nextDrill) {
                is DomainResult.Err -> {
                    // No more vocabulary available — end the session early with a summary rather than erroring.
                    finishSession(run, drillOutcome.wasCorrect, drillOutcome.feedback)
                }
                is DomainResult.Ok -> DomainResult.Ok(
                    SubmitSessionAnswerResult(
                        wasCorrect = drillOutcome.wasCorrect,
                        feedback = drillOutcome.feedback,
                        nextPrompt = nextDrill.value.promptText,
                        nextTutoringSessionId = nextDrill.value.sessionId,
                        sessionCompleted = false,
                        summary = null,
                        nextExerciseKind = nextDrill.value.exerciseKind,
                        nextOptions = nextDrill.value.options,
                        nextMatchPairs = nextDrill.value.matchPairs,
                    ),
                )
            }
        }

        return finishSession(run, drillOutcome.wasCorrect, drillOutcome.feedback)
    }

    private suspend fun finishSession(run: LearningSessionRun, lastWasCorrect: Boolean, lastFeedback: String): DomainResult<SubmitSessionAnswerResult> {
        val completed = run.complete()
        sessionRunRepository.save(completed)

        val allStates = learnerVocabularyStateRepository.findAllForLearner(completed.learnerId)
        val touchedStates = allStates.filter { it.vocabularyItemId in completed.touchedVocabularyItemIds }
        val mastered = touchedStates.count { it.status == MasteryStatus.MASTERED || it.status == MasteryStatus.ALMOST_MASTERED }
        val needingPractice = touchedStates.count { it.status == MasteryStatus.NEEDS_PRACTICE }

        val durationMinutes = Duration.between(completed.startedAt, completed.endedAt ?: Instant.now()).toMinutes()
        val accuracy = if (completed.questionsAsked == 0) {
            0
        } else {
            (completed.correctCount * 100) / completed.questionsAsked
        }

        val summary = SessionSummary(
            durationMinutes = durationMinutes,
            questionsAsked = completed.questionsAsked,
            correctCount = completed.correctCount,
            incorrectCount = completed.incorrectCount,
            accuracyPercent = accuracy,
            wordsMastered = mastered,
            wordsNeedingPractice = needingPractice,
        )

        return DomainResult.Ok(
            SubmitSessionAnswerResult(
                wasCorrect = lastWasCorrect,
                feedback = lastFeedback,
                nextPrompt = null,
                nextTutoringSessionId = null,
                sessionCompleted = true,
                summary = summary,
            ),
        )
    }
}
