package evola.tutoring.domain

/**
 * Pure, deterministic follow-up choice ("What's the plural form?" / "Use it in a sentence") —
 * no LLM call needed to decide which follow-up to ask, only to render it.
 */
object FollowUpSelector {
    fun selectFollowUp(tier: DifficultyTier, partOfSpeech: String?): ExerciseKind? = when {
        tier in setOf(DifficultyTier.BEGINNER, DifficultyTier.INTERMEDIATE) && partOfSpeech == "noun" ->
            ExerciseKind.TRANSLATE // plural-form prompt — reuses TRANSLATE grading with a different prompt source
        tier in setOf(DifficultyTier.ADVANCED, DifficultyTier.EXPERT) -> ExerciseKind.SENTENCE_CREATION
        else -> null
    }
}
