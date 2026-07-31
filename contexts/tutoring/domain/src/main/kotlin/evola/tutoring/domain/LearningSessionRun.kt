package evola.tutoring.domain

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.VocabularyItemId
import java.time.Duration
import java.time.Instant

enum class SessionBudgetType { DURATION_MINUTES, WORD_COUNT }

/**
 * A bounded, multi-question session (see /learn) — a thin orchestration wrapper around the
 * single-word drills already built for /practice; it only tracks the budget and rolling counters,
 * never grading/SM-2/mastery logic (that stays exactly where Milestone 3 built it).
 */
data class LearningSessionRun(
    val id: LearningSessionRunId,
    val learnerId: LearnerId,
    val budgetType: SessionBudgetType,
    val budgetValue: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
    val questionsAsked: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val touchedVocabularyItemIds: List<VocabularyItemId>,
    /** Mini App session config, fixed for the life of the session so every question honors it. */
    val allowedKinds: Set<ExerciseKind> = emptySet(),
    val difficultyOverride: DifficultyTier? = null,
) {
    fun isBudgetExhausted(now: Instant = Instant.now()): Boolean = when (budgetType) {
        SessionBudgetType.WORD_COUNT -> questionsAsked >= budgetValue
        SessionBudgetType.DURATION_MINUTES -> Duration.between(startedAt, now).toMinutes() >= budgetValue
    }

    fun recordAnswer(wasCorrect: Boolean, vocabularyItemId: VocabularyItemId): LearningSessionRun = copy(
        questionsAsked = questionsAsked + 1,
        correctCount = correctCount + if (wasCorrect) 1 else 0,
        incorrectCount = incorrectCount + if (!wasCorrect) 1 else 0,
        touchedVocabularyItemIds = (touchedVocabularyItemIds + vocabularyItemId).distinct(),
    )

    fun complete(now: Instant = Instant.now()): LearningSessionRun = copy(endedAt = now)

    companion object {
        fun start(
            id: LearningSessionRunId,
            learnerId: LearnerId,
            budgetType: SessionBudgetType,
            budgetValue: Int,
            allowedKinds: Set<ExerciseKind> = emptySet(),
            difficultyOverride: DifficultyTier? = null,
            now: Instant = Instant.now(),
        ) = LearningSessionRun(
            id = id, learnerId = learnerId, budgetType = budgetType, budgetValue = budgetValue,
            startedAt = now, endedAt = null, questionsAsked = 0, correctCount = 0, incorrectCount = 0,
            touchedVocabularyItemIds = emptyList(),
            allowedKinds = allowedKinds, difficultyOverride = difficultyOverride,
        )
    }
}
