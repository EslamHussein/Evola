package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.core.kernel.DialogueTurnId
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.TurnRole
import evola.vocabulary.domain.ReviewGrader
import java.time.Instant

data class SubmitGrammarAnswerCommand(val learnerId: LearnerId, val sessionId: TutoringSessionId, val rawAnswer: String) :
    Command<DomainResult<SubmitGrammarAnswerResult>>

data class SubmitGrammarAnswerResult(val wasCorrect: Boolean, val feedback: String)

class SubmitGrammarAnswerHandler(
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
) : UseCase<SubmitGrammarAnswerCommand, DomainResult<SubmitGrammarAnswerResult>> {

    override suspend fun handle(input: SubmitGrammarAnswerCommand): DomainResult<SubmitGrammarAnswerResult> {
        val session = sessionRepository.findById(input.sessionId)
            ?: return DomainResult.Err(DomainError.NotFound("No such tutoring session"))
        if (session.learnerId != input.learnerId) {
            return DomainResult.Err(DomainError.Conflict("Session does not belong to the requesting learner"))
        }

        val turns = turnRepository.findBySession(session.id)
        val prompt = turns.lastOrNull { it.role == TurnRole.PROMPT }
            ?: return DomainResult.Err(DomainError.ValidationFailed("No pending question for this session"))

        val wasCorrect = ReviewGrader.grade(prompt.correctAnswer.orEmpty(), input.rawAnswer) >= 3
        val feedback = buildString {
            append(if (wasCorrect) "Richtig!" else "Nicht ganz — richtige Antwort: ${prompt.correctAnswer}")
            prompt.explanation?.let { append(" $it") }
        }

        val now = Instant.now()
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, prompt.turnIndex + 1, TurnRole.LEARNER_ANSWER,
                prompt.exerciseKind, input.rawAnswer, null, null, wasCorrect, now,
            ),
        )
        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, prompt.turnIndex + 2, TurnRole.FEEDBACK,
                prompt.exerciseKind, feedback, null, null, null, now,
            ),
        )
        sessionRepository.save(session.complete(now))

        return DomainResult.Ok(SubmitGrammarAnswerResult(wasCorrect, feedback))
    }
}
