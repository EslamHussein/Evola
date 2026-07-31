package evola.tutoring.application

import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.GeneratePracticeExerciseRequest
import evola.integrations.aigateway.PracticeExerciseKind
import evola.tutoring.domain.DifficultyTier

/**
 * Cache-through, mirroring exercise-generation's GenerateExerciseForWordHandler: the same
 * (word|topic, kind, tier) combination never triggers a second LLM call, reused across learners.
 * PLURAL_FORM is tier-independent (a fixed grammatical fact), so its cache key omits the tier.
 */
class GetOrGeneratePracticeExerciseHandler(
    private val wordCache: TutoringWordContentCache,
    private val grammarCache: TutoringGrammarContentCache,
    private val aiTutorPort: AiTutorPort,
) {
    suspend fun forWord(
        vocabularyItemId: VocabularyItemId,
        germanWord: String,
        englishTranslation: String,
        cefrLevel: String,
        kind: PracticeExerciseKind,
        tier: DifficultyTier,
    ): CachedPracticeContent {
        val tierKey = if (kind == PracticeExerciseKind.PLURAL_FORM) null else tier.name
        wordCache.find(vocabularyItemId, kind.name, tierKey)?.let { return it }

        val generated = aiTutorPort.generatePracticeExercise(
            GeneratePracticeExerciseRequest(
                kind = kind,
                germanWord = germanWord,
                englishTranslation = englishTranslation,
                cefrLevel = cefrLevel,
                difficultyTier = tier.name,
            ),
        )
        val content = CachedPracticeContent(
            promptText = generated.promptText,
            correctAnswer = generated.correctAnswer,
            hint = generated.hint,
            explanation = generated.explanation,
            options = generated.options,
            matchPairs = generated.matchPairs,
            modelUsed = generated.modelUsed,
        )
        wordCache.store(vocabularyItemId, kind.name, tierKey, content)
        return content
    }

    suspend fun forGrammarTopic(grammarTopic: String, cefrLevel: String, tier: DifficultyTier): CachedPracticeContent {
        grammarCache.find(grammarTopic, tier.name)?.let { return it }

        val generated = aiTutorPort.generatePracticeExercise(
            GeneratePracticeExerciseRequest(
                kind = PracticeExerciseKind.GRAMMAR_CHOICE,
                grammarTopic = grammarTopic,
                cefrLevel = cefrLevel,
                difficultyTier = tier.name,
            ),
        )
        val content = CachedPracticeContent(
            promptText = generated.promptText,
            correctAnswer = generated.correctAnswer,
            hint = generated.hint,
            explanation = generated.explanation,
            options = generated.options,
            modelUsed = generated.modelUsed,
        )
        grammarCache.store(grammarTopic, tier.name, content)
        return content
    }
}
