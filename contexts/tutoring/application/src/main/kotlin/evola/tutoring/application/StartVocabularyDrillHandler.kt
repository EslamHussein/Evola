package evola.tutoring.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.DialogueTurnId
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerId
import evola.core.kernel.LearnerVocabularyStateId
import evola.core.kernel.TutoringSessionId
import evola.integrations.aigateway.MatchPair
import evola.integrations.aigateway.PracticeExerciseKind
import evola.tutoring.domain.AdaptiveDifficultySelector
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.DifficultyTier
import evola.tutoring.domain.ExerciseKind
import evola.tutoring.domain.TurnRole
import evola.tutoring.domain.TutoringSession
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.application.ReviewHistoryRepository
import evola.vocabulary.application.VocabularyItemRepository
import evola.vocabulary.domain.LearnerVocabularyState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

data class StartVocabularyDrillCommand(
    val learnerId: LearnerId,
    /** Mini App session config: restricts which kinds are eligible; null/empty reproduces today's auto-tier behavior ("Mixed"). */
    val allowedKinds: Set<ExerciseKind>? = null,
    /** Mini App session config: fixes the tier instead of running AdaptiveDifficultySelector ("Adaptive" = null). */
    val difficultyOverride: DifficultyTier? = null,
) : Command<DomainResult<StartDrillResult>>

data class StartDrillResult(
    val sessionId: TutoringSessionId,
    val promptText: String,
    val exerciseKind: ExerciseKind,
    val options: List<String>? = null,
    val matchPairs: List<MatchPair>? = null,
)

/**
 * Adaptive entry point layered alongside (not replacing) the fixed Milestone-1 /learn: picks a
 * due review if one exists, otherwise a brand-new word, then generates an exercise at a tier
 * chosen deterministically from mastery status + recent outcomes (see AdaptiveDifficultySelector).
 */
class StartVocabularyDrillHandler(
    private val stateRepository: LearnerVocabularyStateRepository,
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val reviewHistoryRepository: ReviewHistoryRepository,
    private val sessionRepository: TutoringSessionRepository,
    private val turnRepository: DialogueTurnRepository,
    private val contentProvider: GetOrGeneratePracticeExerciseHandler,
) : UseCase<StartVocabularyDrillCommand, DomainResult<StartDrillResult>> {

    override suspend fun handle(input: StartVocabularyDrillCommand): DomainResult<StartDrillResult> {
        val state = pickState(input.learnerId)
            ?: return DomainResult.Err(DomainError.NotFound("No vocabulary available to practice"))
        val item = vocabularyItemRepository.findById(state.vocabularyItemId)
            ?: return DomainResult.Err(DomainError.NotFound("Vocabulary item no longer exists"))

        val recentOutcomes = reviewHistoryRepository.findRecentForState(state.id, limit = 3)
            .sortedBy { it.reviewedAt }
            .map { it.wasCorrect }
        val tier = input.difficultyOverride ?: AdaptiveDifficultySelector.selectTier(state.status, recentOutcomes)
        val kind = exerciseKindFor(tier, input.allowedKinds, item.article != null)

        val session = TutoringSession.startVocabularyDrill(TutoringSessionId.new(), input.learnerId, item.id)
        sessionRepository.save(session)

        var options: List<String>? = null
        var matchPairs: List<MatchPair>? = null

        val (promptText, correctAnswer, explanation) = when (kind) {
            ExerciseKind.TRANSLATE ->
                Triple("What is the German word for \"${item.englishTranslation}\"?", item.germanWord, null)

            ExerciseKind.SENTENCE_CREATION ->
                Triple("Create a sentence in German using the word \"${item.germanWord}\".", null, null)

            ExerciseKind.ARTICLE_CHOICE -> {
                options = listOf("der", "die", "das")
                Triple("What is the correct article for \"${item.germanWord}\"?", item.article, null)
            }

            ExerciseKind.WORD_MATCHING -> {
                val generated = contentProvider.forWord(
                    vocabularyItemId = item.id,
                    germanWord = item.germanWord,
                    englishTranslation = item.englishTranslation,
                    cefrLevel = item.cefrLevel.code,
                    kind = PracticeExerciseKind.WORD_MATCHING,
                    tier = tier,
                )
                matchPairs = generated.matchPairs
                Triple(generated.promptText, generated.matchPairs?.let { MATCH_PAIRS_JSON.encodeToString(it) }, generated.explanation)
            }

            else -> {
                val generated = contentProvider.forWord(
                    vocabularyItemId = item.id,
                    germanWord = item.germanWord,
                    englishTranslation = item.englishTranslation,
                    cefrLevel = item.cefrLevel.code,
                    kind = practiceKindFor(kind),
                    tier = tier,
                )
                options = generated.options
                Triple(generated.promptText, generated.correctAnswer, generated.explanation)
            }
        }

        turnRepository.append(
            DialogueTurn(
                id = DialogueTurnId.new(),
                sessionId = session.id,
                turnIndex = 0,
                role = TurnRole.PROMPT,
                exerciseKind = kind,
                content = promptText,
                correctAnswer = correctAnswer,
                explanation = explanation,
                wasCorrect = null,
                createdAt = Instant.now(),
            ),
        )

        return DomainResult.Ok(StartDrillResult(session.id, promptText, kind, options, matchPairs))
    }

    private suspend fun pickState(learnerId: LearnerId): LearnerVocabularyState? {
        stateRepository.findDueForLearner(learnerId, limit = 1).firstOrNull()?.let { return it }
        val item = vocabularyItemRepository.findNextUnseenFor(learnerId) ?: return null
        val newState = LearnerVocabularyState.newFor(LearnerVocabularyStateId.new(), learnerId, item.id)
        stateRepository.save(newState)
        return newState
    }

    /**
     * `allowedKinds` (Mini App "question types" filter) narrows the pool the tier would otherwise
     * pick from; null/empty reproduces today's exact tier-only behavior ("Mixed"/no filter — used
     * by /practice and the chat-based /learn, which never pass it). ARTICLE_CHOICE is only
     * eligible when the word actually has an article on file.
     */
    private fun exerciseKindFor(tier: DifficultyTier, allowedKinds: Set<ExerciseKind>?, hasArticle: Boolean): ExerciseKind {
        val default = when (tier) {
            DifficultyTier.BEGINNER -> ExerciseKind.TRANSLATE
            DifficultyTier.INTERMEDIATE -> ExerciseKind.MULTIPLE_CHOICE
            DifficultyTier.ADVANCED -> ExerciseKind.CLOZE_SENTENCE
            DifficultyTier.EXPERT -> ExerciseKind.SENTENCE_CREATION
        }
        if (allowedKinds.isNullOrEmpty()) return default

        val eligible = allowedKinds.filter { it != ExerciseKind.ARTICLE_CHOICE || hasArticle }
        if (eligible.isEmpty()) return default
        return if (default in eligible) default else eligible.random()
    }

    private fun practiceKindFor(kind: ExerciseKind): PracticeExerciseKind = when (kind) {
        ExerciseKind.MULTIPLE_CHOICE -> PracticeExerciseKind.MULTIPLE_CHOICE
        ExerciseKind.CLOZE_SENTENCE -> PracticeExerciseKind.CLOZE_SENTENCE
        ExerciseKind.SCENARIO_CLOZE -> PracticeExerciseKind.SCENARIO_CLOZE
        ExerciseKind.TRANSLATE_TO_ENGLISH -> PracticeExerciseKind.TRANSLATE_TO_ENGLISH
        ExerciseKind.TRUE_FALSE -> PracticeExerciseKind.TRUE_FALSE
        else -> PracticeExerciseKind.CLOZE_SENTENCE
    }

    companion object {
        private val MATCH_PAIRS_JSON = Json { ignoreUnknownKeys = true }
    }
}
