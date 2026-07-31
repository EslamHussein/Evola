package evola.tutoring.domain

/**
 * Maps to the spec's Beginner/Intermediate/Advanced/Expert progression (see [DifficultyTier]).
 */
enum class ExerciseKind {
    TRANSLATE,          // Beginner — deterministic, templated
    MULTIPLE_CHOICE,    // Intermediate — deterministic for nouns (article drill), AI-generated distractors otherwise
    CLOZE_SENTENCE,     // Advanced — built from a cached example sentence when possible (ClozeBuilder), AI fallback
    SCENARIO_CLOZE,     // Advanced (context-based) — always AI-generated, cached
    SENTENCE_CREATION,  // Expert — templated prompt, AI-evaluated answer
    GRAMMAR_CHOICE,     // Grammar mode — AI-generated A/B content, deterministic grading + AI mistake explanation
    SPEAKING_TURN,      // Speaking mode — always AI, never cached
    TRANSLATE_TO_ENGLISH, // German -> English direction, complements TRANSLATE (English -> German)
    TRUE_FALSE,         // simple true/false fact about a word or grammar rule
    ARTICLE_CHOICE,     // deterministic der/die/das quiz from VocabularyItem.article, no AI call
    WORD_MATCHING,      // Mini App only — AI picks 4-6 related words, learner pairs them with translations
}

enum class DifficultyTier {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT;

    fun stepUp(): DifficultyTier = entries.getOrElse(ordinal + 1) { this }
    fun stepDown(): DifficultyTier = entries.getOrElse(ordinal - 1) { this }
}
