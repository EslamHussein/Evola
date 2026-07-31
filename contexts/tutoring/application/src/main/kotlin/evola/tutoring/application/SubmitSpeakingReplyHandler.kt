package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DialogueTurnId
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.RunSpeakingTurnRequest
import evola.integrations.aigateway.SpeakingConversationTurn
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.SpeakingScenarioCatalog
import evola.tutoring.domain.TurnRole
import java.time.Instant

data class SubmitSpeakingReplyCommand(val learnerId: LearnerId, val sessionId: TutoringSessionId, val rawAnswer: String) :
    Command<DomainResult<SubmitSpeakingReplyResult>>

data class SubmitSpeakingReplyResult(
    val correctedReply: String?,
    val correctionExplanation: String?,
    val aiNextLine: String,
    val scenarioCompleted: Boolean,
)

class SubmitSpeakingReplyHandler(
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
    private val aiTutorPort: AiTutorPort,
) : UseCase<SubmitSpeakingReplyCommand, DomainResult<SubmitSpeakingReplyResult>> {

    override suspend fun handle(input: SubmitSpeakingReplyCommand): DomainResult<SubmitSpeakingReplyResult> {
        val session = sessionRepository.findById(input.sessionId)
            ?: return DomainResult.Err(DomainError.NotFound("No such tutoring session"))
        if (session.learnerId != input.learnerId) {
            return DomainResult.Err(DomainError.Conflict("Session does not belong to the requesting learner"))
        }
        val scenario = session.focusGrammarTopic?.let { SpeakingScenarioCatalog.findByTitle(it) }
            ?: return DomainResult.Err(DomainError.ValidationFailed("Session has no scenario"))

        val turns = turnRepository.findBySession(session.id)
        val history = turns
            .filter { it.role == TurnRole.PROMPT || it.role == TurnRole.FOLLOW_UP_PROMPT || it.role == TurnRole.LEARNER_ANSWER }
            .sortedBy { it.turnIndex }
            .map { turn ->
                SpeakingConversationTurn(
                    speaker = if (turn.role == TurnRole.LEARNER_ANSWER) "LEARNER" else "AI",
                    text = turn.content,
                )
            }

        val result = aiTutorPort.runSpeakingTurn(
            RunSpeakingTurnRequest(
                scenarioDescription = scenario.description,
                conversationHistory = history,
                learnerLatestReply = input.rawAnswer,
                cefrLevel = "B1",
            ),
        )

        val now = Instant.now()
        val lastIndex = turns.maxOfOrNull { it.turnIndex } ?: 0
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, lastIndex + 1, TurnRole.LEARNER_ANSWER, ExerciseKind.SPEAKING_TURN,
                input.rawAnswer, null, result.correctionExplanation, result.correctedReply == null, now,
            ),
        )
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, lastIndex + 2, TurnRole.FOLLOW_UP_PROMPT, ExerciseKind.SPEAKING_TURN,
                result.aiNextLine, null, null, null, now,
            ),
        )

        if (result.scenarioComplete) {
            sessionRepository.save(session.complete(now))
        }

        return DomainResult.Ok(
            SubmitSpeakingReplyResult(result.correctedReply, result.correctionExplanation, result.aiNextLine, result.scenarioComplete),
        )
    }
}
