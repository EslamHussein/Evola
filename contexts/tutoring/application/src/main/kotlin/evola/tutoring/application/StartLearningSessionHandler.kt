package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.MatchPair
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.LearningSessionRun
import evola.tutoring.domain.SessionBudgetType

data class StartLearningSessionCommand(
    val learnerId: LearnerId,
    val budgetType: SessionBudgetType,
    val budgetValue: Int,
    val allowedKinds: Set<ExerciseKind>? = null,
    val difficultyOverride: DifficultyTier? = null,
) : Command<DomainResult<StartSessionResult>>

data class StartSessionResult(
    val sessionRunId: LearningSessionRunId,
    val tutoringSessionId: TutoringSessionId,
    val promptText: String,
    val exerciseKind: ExerciseKind,
    val options: List<String>? = null,
    val matchPairs: List<MatchPair>? = null,
)

/**
 * Thin orchestration on top of the existing single-word drill (/practice's StartVocabularyDrillHandler,
 * reused unchanged) — this just wraps the first drill in a budgeted LearningSessionRun container.
 */
class StartLearningSessionHandler(
    private val sessionRunRepository: LearningSessionRunRepository,
    private val startVocabularyDrillHandler: StartVocabularyDrillHandler,
) : UseCase<StartLearningSessionCommand, DomainResult<StartSessionResult>> {

    override suspend fun handle(input: StartLearningSessionCommand): DomainResult<StartSessionResult> {
        val drillResult = startVocabularyDrillHandler.handle(
            StartVocabularyDrillCommand(input.learnerId, input.allowedKinds, input.difficultyOverride),
        )
        return when (drillResult) {
            is DomainResult.Err -> drillResult
            is DomainResult.Ok -> {
                val run = LearningSessionRun.start(
                    id = LearningSessionRunId.new(),
                    learnerId = input.learnerId,
                    budgetType = input.budgetType,
                    budgetValue = input.budgetValue,
                    allowedKinds = input.allowedKinds ?: emptySet(),
                    difficultyOverride = input.difficultyOverride,
                )
                sessionRunRepository.save(run)
                DomainResult.Ok(
                    StartSessionResult(
                        sessionRunId = run.id,
                        tutoringSessionId = drillResult.value.sessionId,
                        promptText = drillResult.value.promptText,
                        exerciseKind = drillResult.value.exerciseKind,
                        options = drillResult.value.options,
                        matchPairs = drillResult.value.matchPairs,
                    ),
                )
            }
        }
    }
}
