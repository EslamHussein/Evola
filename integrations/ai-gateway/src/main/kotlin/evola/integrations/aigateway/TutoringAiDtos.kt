package evola.integrations.aigateway

import kotlinx.serialization.Serializable

enum class PracticeExerciseKind { MULTIPLE_CHOICE, SCENARIO_CLOZE, CLOZE_SENTENCE, GRAMMAR_CHOICE, PLURAL_FORM, TRANSLATE_TO_ENGLISH, TRUE_FALSE, WORD_MATCHING }

/** One (German term, English/translation) pair for a WORD_MATCHING exercise. */
@Serializable
data class MatchPair(val left: String, val right: String)

data class GeneratePracticeExerciseRequest(
    val kind: PracticeExerciseKind,
    val germanWord: String? = null,
    val englishTranslation: String? = null,
    val grammarTopic: String? = null,
    val cefrLevel: String,
    val difficultyTier: String,
    val topics: List<String> = emptyList(),
    val sourceExcerpt: String? = null,
)

data class GeneratedPracticeExercise(
    val promptText: String,
    val correctAnswer: String,
    val hint: String? = null,
    val explanation: String? = null,
    /** Multiple-choice / article-choice / true-false options, in display order. Null for free-text kinds. */
    val options: List<String>? = null,
    /** Only populated for [PracticeExerciseKind.WORD_MATCHING]. */
    val matchPairs: List<MatchPair>? = null,
    val modelUsed: String,
)

data class EvaluateFreeformAnswerRequest(
    val exerciseKind: String,
    val prompt: String,
    val targetWordOrTopic: String,
    val learnerAnswer: String,
    val cefrLevel: String,
)

data class FreeformEvaluationResult(
    val isCorrect: Boolean,
    val correctedText: String?,
    val feedback: String,
    /** 0..5 — fed straight into VocabularyReviewService.applyQuality. */
    val qualityScore: Int,
    val modelUsed: String,
)

data class SpeakingConversationTurn(val speaker: String, val text: String) // "AI" | "LEARNER"

data class RunSpeakingTurnRequest(
    val scenarioDescription: String,
    val conversationHistory: List<SpeakingConversationTurn>,
    val learnerLatestReply: String,
    val cefrLevel: String,
)

data class SpeakingTurnResult(
    val correctedReply: String?,
    val correctionExplanation: String?,
    val aiNextLine: String,
    val scenarioComplete: Boolean,
    val modelUsed: String,
)
