package evola.shared.feature.onboarding.domain

import evola.shared.core.common.ApiResult
import evola.shared.language.NativeLanguage

/** Onboarding + Goal Setup per 01_PRODUCT_SPEC.md §1.3-1.4: exactly one active goal per account. */
data class Goal(
    val id: String,
    val goalText: String,
    val title: String?,
    val nativeLanguage: NativeLanguage,
    val isActive: Boolean,
    val createdAt: String,
)

sealed interface CreateGoalResult {
    data class Success(val goal: Goal) : CreateGoalResult
    data object ActiveGoalExists : CreateGoalResult
    data class ValidationError(val message: String) : CreateGoalResult
}

sealed interface UpdateGoalResult {
    data class Success(val goal: Goal) : UpdateGoalResult
    data object NotFound : UpdateGoalResult
    data class ValidationError(val message: String) : UpdateGoalResult
}

/** Lesson Selection (01_PRODUCT_SPEC.md §1.7) - status mirrors the DB's `lesson_status` enum
 * ("pending" | "ready" | "failed"). vocabProgress/grammarProgress are always 0 until M6/M7
 * populate real vocabulary_items/grammar_topics - an honest placeholder, not a fake number. */
data class Lesson(
    val id: String,
    val number: Int,
    val title: String,
    val status: String,
    val vocabProgress: Float,
    val grammarProgress: Float,
    val grammarCount: Int = 0,
) {
    /** Per 01_PRODUCT_SPEC.md §1.7: average of vocab/grammar progress, or vocab alone when the
     * lesson has no grammar topics yet - branches on grammarCount (a real topic count), not on
     * grammarProgress == 0f (a real bug found during M7 planning: that check is indistinguishable
     * from "a real topic sitting at 0% mastery," harmless only while grammarProgress was always
     * hardcoded 0 before M7). */
    val completionPct: Float get() = if (grammarCount == 0) vocabProgress else (vocabProgress + grammarProgress) / 2f
    val isReady: Boolean get() = status == "ready"
}

/** Progress Dashboard aggregate (01_PRODUCT_SPEC.md §1.10). [currentLessonId] is the first lesson
 * still below 100% (null once every lesson is done, or there are none yet); the client resolves it
 * against its own already-fetched lesson list to build the "Continue Lesson N" CTA. */
data class GoalProgress(
    val overallPct: Float,
    val currentLessonId: String?,
    val streakDays: Int,
    val todayCompleted: Boolean,
    val vocabulary: VocabularyBreakdown,
    val nudgeWord: NudgeWord? = null,
    /** The last 7 calendar days (oldest first, today last) for Home's weekly streak strip. */
    val weeklyActivity: List<DayActivity> = emptyList(),
    /** New words learned today vs. the configured daily goal ([evola.shared.local.AppSettings.
     * dailyNewWordGoal]) - a Reword-style "Learned today X/Y" readout. */
    val todayNewWordsLearned: Int = 0,
    val dailyGoal: Int = 0,
    /** Longest all-time consecutive-day run, alongside [streakDays]'s "current" run - Reword's
     * "Best streak" stat. */
    val bestStreakDays: Int = 0,
    /** Reword's "Streak freeze" bank remaining - see [evola.shared.feature.onboarding.data.LocalGoalsRepository]. */
    val streakFreezesAvailable: Int = 0,
    /** Badges unlocked by *this* [getProgress] call, for a one-shot celebration toast - not the full
     * unlocked set (Profile reads that separately via [evola.shared.achievements.AchievementsRepository.
     * unlockedBadgeIds]). Empty on almost every call; non-empty only the first time a threshold is
     * crossed. */
    val newlyUnlockedBadges: List<evola.shared.achievements.BadgeDefinition> = emptyList(),
    /** Reword's Home "Review words - Words to review: N" - goal-wide due count, distinct from
     * [todayNewWordsLearned]/[dailyGoal] which cover the "Learn new words" row instead. */
    val wordsToReviewCount: Int = 0,
)

/** One day's activity for the weekly streak strip - [newWordsLearned]/[wordsReviewed] drive Home's
 * stacked activity chart, [hadActivity] alone drives the strip's filled/outlined circle. */
data class DayActivity(
    val date: String,
    val hadActivity: Boolean,
    val newWordsLearned: Int,
    val wordsReviewed: Int,
)

/** The in-progress word closest to "mastered" on the vocabulary SRS ladder, for Home's "N
 * review(s) from mastering <term>" nudge. [reviewsRemaining] = correct answers still needed. */
data class NudgeWord(val term: String, val reviewsRemaining: Int)

/** Word counts across every lesson in the goal, bucketed from the 5-status vocabulary SRS ladder
 * (evola.shared.feature.vocabulary.domain.VocabularySrs.STATUSES): "unseen" -> [notStarted], "mastered" ->
 * [mastered], anything in between (introduced/learning/review) -> [inProgress].
 *
 * [struggling] is a separate, overlapping-with-neither-bucket cut of the same words: those whose
 * most recent answer was wrong (incorrect_streak > 0). A word can't be both [mastered] and
 * [struggling] - any wrong answer demotes it out of "mastered" immediately (see
 * VocabularySrs.onIncorrect) - so callers building a 3-way red/yellow/green view can safely use
 * struggling (red) / mastered (green) / `total - struggling - mastered` (yellow). */
data class VocabularyBreakdown(
    val notStarted: Int,
    val inProgress: Int,
    val mastered: Int,
    val struggling: Int,
)

interface GoalsRepository {
    // createGoal/updateGoal keep their own sealed result types — they model domain-specific
    // outcomes (active-goal conflict, validation) that a generic ApiResult would flatten.
    suspend fun createGoal(goalText: String, title: String?, nativeLanguage: NativeLanguage): CreateGoalResult
    suspend fun updateGoal(goalId: String, goalText: String?, title: String?, nativeLanguage: NativeLanguage?): UpdateGoalResult
    /** Success(null) means "no active goal yet" (a real state → onboarding), distinct from a
     * Failure (network/server) — the old `Goal?` couldn't tell them apart. */
    suspend fun getActiveGoal(): ApiResult<Goal?>
    suspend fun listLessons(goalId: String): ApiResult<List<Lesson>>
    suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress>
}
