package evola.tutoring.domain

enum class DifficultyTier {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT;

    fun stepUp(): DifficultyTier = entries.getOrElse(ordinal + 1) { this }
    fun stepDown(): DifficultyTier = entries.getOrElse(ordinal - 1) { this }
}
