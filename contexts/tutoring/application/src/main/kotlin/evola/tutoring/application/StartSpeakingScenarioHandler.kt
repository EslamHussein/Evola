package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DialogueTurnId
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.RunSpeakingTurnRequest
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.SpeakingScenarioCatalog
import evola.tutoring.domain.TurnRole
import evola.tutoring.domain.TutoringSession
import java.time.Instant

data class StartSpeakingScenarioCommand(val learnerId: LearnerId, val scenarioTitle: String? = null) :
    Command<DomainResult<StartDrillResult>>

class StartSpeakingScenarioHandler(
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
    private val aiTutorPort: AiTutorPort,
) : UseCase<StartSpeakingScenarioCommand, DomainResult<StartDrillResult>> {

    override suspend fun handle(input: StartSpeakingScenarioCommand): DomainResult<StartDrillResult> {
        val scenario = input.scenarioTitle?.let { SpeakingScenarioCatalog.findByTitle(it) }
            ?: SpeakingScenarioCatalog.pickFor(input.learnerId.value.hashCode())

        val session = TutoringSession.startSpeakingScenario(TutoringSessionId.new(), input.learnerId, scenario.title)
        sessionRepository.save(session)

        // Empty learnerLatestReply/conversationHistory signals "open the conversation" — the
        // system prompt instructs the model to omit correction fields in that case.
        val result = aiTutorPort.runSpeakingTurn(
            RunSpeakingTurnRequest(
                scenarioDescription = scenario.description,
                conversationHistory = emptyList(),
                learnerLatestReply = "",
                cefrLevel = "B1",
            ),
        )

        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, 0, TurnRole.PROMPT, ExerciseKind.SPEAKING_TURN,
                result.aiNextLine, null, null, null, Instant.now(),
            ),
        )

        return DomainResult.Ok(StartDrillResult(session.id, result.aiNextLine, ExerciseKind.SPEAKING_TURN))
    }
}
