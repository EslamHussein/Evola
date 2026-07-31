package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DialogueTurnId
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.GrammarTopicCatalog
import evola.tutoring.domain.TurnRole
import evola.tutoring.domain.TutoringSession
import java.time.Instant

data class StartGrammarExerciseCommand(val learnerId: LearnerId, val grammarTopic: String? = null) :
    Command<DomainResult<StartDrillResult>>

class StartGrammarExerciseHandler(
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
    private val contentProvider: GetOrGeneratePracticeExerciseHandler,
) : UseCase<StartGrammarExerciseCommand, DomainResult<StartDrillResult>> {

    override suspend fun handle(input: StartGrammarExerciseCommand): DomainResult<StartDrillResult> {
        val topic = input.grammarTopic ?: GrammarTopicCatalog.pickFor(input.learnerId.value.hashCode())
        val session = TutoringSession.startGrammarDrill(TutoringSessionId.new(), input.learnerId, topic)
        sessionRepository.save(session)

        // Grammar exercises aren't tied to a single vocabulary item's mastery — a fixed
        // mid-tier default is used; adaptive difficulty for Grammar Mode is a future refinement.
        val generated = contentProvider.forGrammarTopic(topic, cefrLevel = "B1", tier = DifficultyTier.INTERMEDIATE)

        turnRepository.append(
            DialogueTurn(
                DialogueTurnId.new(), session.id, 0, TurnRole.PROMPT, ExerciseKind.GRAMMAR_CHOICE,
                generated.promptText, generated.correctAnswer, generated.explanation, null, Instant.now(),
            ),
        )

        return DomainResult.Ok(StartDrillResult(session.id, generated.promptText, ExerciseKind.GRAMMAR_CHOICE, generated.options))
    }
}
