package evola.shared.feature.onboarding.data

import evola.database.AppDatabase
import evola.database.entity.GoalEntity
import evola.shared.feature.profile.domain.AchievementsRepository
import evola.shared.core.common.ApiResult
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.grammarTopicCountRoom
import evola.shared.core.common.lessonGrammarProgressRoom
import evola.shared.core.common.lessonVocabProgressRoom
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.feature.onboarding.domain.DayActivity
import evola.shared.feature.onboarding.domain.Goal
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.GoalsRepository
import evola.shared.feature.onboarding.domain.Lesson
import evola.shared.feature.onboarding.domain.NudgeWord
import evola.shared.feature.onboarding.domain.UpdateGoalResult
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import evola.shared.language.NativeLanguage
import evola.shared.feature.profile.domain.SettingsRepository
import evola.shared.core.common.srs.computeBestStreak
import evola.shared.core.common.srs.computeStreak
import evola.shared.feature.vocabulary.domain.VocabularySrs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val WEEK_LENGTH = 7

/** Fully local goals/lessons/progress over Room — no server. Implements the same
 * [GoalsRepository] the ViewModels already use, so nothing above it changes. Progress computation
 * mirrors the retired server `GoalService`. */
class LocalGoalsRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val achievementsRepository: AchievementsRepository,
) : GoalsRepository {

    override suspend fun createGoal(goalText: String, title: String?, nativeLanguage: NativeLanguage): CreateGoalResult {
        val text = goalText.trim()
        if (text.length !in 3..200) {
            EvolaLog.d("goals", "createGoal validation failed: length=${text.length}")
            return CreateGoalResult.ValidationError("Goal text must be 3-200 characters.")
        }
        val resolvedTitle = title?.trim()?.ifBlank { null } ?: autoTitle(text)
        val now = nowMillis()
        db.goalDao().deactivateAll(LOCAL_USER)
        val id = newId()
        db.goalDao().insert(GoalEntity(id, LOCAL_USER, text, resolvedTitle, nativeLanguage.code, 1L, now, now))
        return CreateGoalResult.Success(Goal(id, text, resolvedTitle, nativeLanguage, isActive = true, createdAt = now.toString()))
    }

    override suspend fun updateGoal(goalId: String, goalText: String?, title: String?, nativeLanguage: NativeLanguage?): UpdateGoalResult {
        val existing = db.goalDao().selectById(goalId)
            ?: run {
                EvolaLog.d("goals", "updateGoal: not found goalId=$goalId")
                return UpdateGoalResult.NotFound
            }
        val newText = goalText?.trim() ?: existing.goalText
        if (newText.length !in 3..200) {
            EvolaLog.d("goals", "updateGoal validation failed: goalId=$goalId length=${newText.length}")
            return UpdateGoalResult.ValidationError("Goal text must be 3-200 characters.")
        }
        val newTitle = title?.trim()?.ifBlank { null } ?: existing.title
        val newNativeLanguage = nativeLanguage ?: NativeLanguage.fromCode(existing.nativeLanguage)
        db.goalDao().update(newText, newTitle, newNativeLanguage.code, nowMillis(), goalId)
        return UpdateGoalResult.Success(Goal(goalId, newText, newTitle, newNativeLanguage, existing.isActive == 1L, existing.createdAt.toString()))
    }

    override suspend fun getActiveGoal(): ApiResult<Goal?> {
        val row = db.goalDao().selectActive(LOCAL_USER)
        return ApiResult.Success(
            row?.let { Goal(it.id, it.goalText, it.title, NativeLanguage.fromCode(it.nativeLanguage), it.isActive == 1L, it.createdAt.toString()) },
        )
    }

    override suspend fun listLessons(goalId: String): ApiResult<List<Lesson>> {
        val lessons = db.lessonDao().selectByGoal(goalId).map { row ->
            Lesson(
                id = row.id,
                number = row.number.toInt(),
                title = row.title,
                status = row.status,
                vocabProgress = db.lessonVocabProgressRoom(row.id),
                grammarProgress = db.lessonGrammarProgressRoom(row.id),
                grammarCount = db.grammarTopicCountRoom(row.id),
            )
        }
        return ApiResult.Success(lessons)
    }

    override suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress> {
        val lessons = (listLessons(goalId) as ApiResult.Success).data
        val overall = if (lessons.isEmpty()) 0f else lessons.map { it.completionPct }.average().toFloat()
        val currentLessonId = lessons.firstOrNull { it.completionPct < 1f }?.id

        val today = runCatching { LocalDate.parse(localDate) }.getOrNull()
        val rawActivityDates = db.activityDao().completedDates(LOCAL_USER)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        if (today != null) maybeApplyStreakFreeze(today, rawActivityDates)
        val frozenDates = db.activityDao().frozenDates(LOCAL_USER)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val activityDates = rawActivityDates + frozenDates
        val streak = if (today != null) computeStreak(activityDates, today) else 0
        val bestStreak = maxOf(computeBestStreak(activityDates), streak)
        val todayCompleted = today != null && today in rawActivityDates
        val vocabulary = vocabularyBreakdown(goalId)
        val nudgeWord = nudgeWord(goalId)
        // The weekly strip/chart shows real activity only - a frozen day still reads as "no
        // activity" there (streak continuity is the only thing a freeze bridges).
        val weeklyActivity = if (today != null) weeklyActivity(today, rawActivityDates) else emptyList()
        val settings = settingsRepository.current()
        val dailyGoal = settings.dailyNewWordGoal
        val todayNewWordsLearned = weeklyActivity.lastOrNull()?.newWordsLearned ?: 0
        val masteredCount = db.vocabularyDao().masteredCountForUser(LOCAL_USER).toInt()
        val newlyUnlockedBadges = (achievementsRepository.checkAndUnlock(masteredCount, streak) as? ApiResult.Success)?.data ?: emptyList()
        val wordsToReviewCount = db.vocabularyDao().dueCountForGoal(LOCAL_USER, goalId, nowMillis()).toInt()

        return ApiResult.Success(
            GoalProgress(
                overall, currentLessonId, streak, todayCompleted, vocabulary, nudgeWord,
                weeklyActivity, todayNewWordsLearned, dailyGoal, bestStreak, settings.streakFreezesAvailable,
                newlyUnlockedBadges, wordsToReviewCount,
            ),
        )
    }

    /** Reword's "Streak freeze" - if yesterday has no real activity but the day before does (i.e.
     * today's streak is about to break), and a freeze is available, spend one to bridge the gap. Only
     * ever looks at yesterday (never further back) and only ever inserts once per date (`INSERT OR
     * IGNORE` + the table's own unique constraint), so this is safe to call on every [getProgress] -
     * a freeze is spent at most once per gap, not once per call. */
    private suspend fun maybeApplyStreakFreeze(today: LocalDate, activityDates: Set<LocalDate>) {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val dayBefore = today.minus(2, DateTimeUnit.DAY)
        if (yesterday in activityDates || dayBefore !in activityDates) return
        val alreadyFrozen = db.activityDao().frozenDates(LOCAL_USER).contains(yesterday.toString())
        if (alreadyFrozen) return
        val available = settingsRepository.current().streakFreezesAvailable
        if (available <= 0) return
        db.activityDao().insertFreeze(evola.database.entity.StreakFreezeDateEntity(newId(), LOCAL_USER, yesterday.toString()))
        settingsRepository.setStreakFreezesAvailable(available - 1)
    }

    /** The last [WEEK_LENGTH] calendar days (oldest first, [today] last), for Home's weekly streak
     * strip + stacked activity chart. [hadActivity] comes from the same [activityDates] set
     * [computeStreak] uses; per-day new/review counts come from `vocabulary_sessions.dailyCounts`,
     * grouped by each session's own stored local date (see Vocabulary.sq). A day with zero sessions
     * simply doesn't appear in the query result, so it's defaulted to 0/0 below. */
    private suspend fun weeklyActivity(today: LocalDate, activityDates: Set<LocalDate>): List<DayActivity> {
        val since = today.minus(WEEK_LENGTH - 1, DateTimeUnit.DAY)
        val counts = db.vocabularyDao().dailyCounts(LOCAL_USER, since.toString())
            .associateBy({ it.localDate }, { (it.newWords ?: 0L).toInt() to (it.reviewWords ?: 0L).toInt() })
        return (0 until WEEK_LENGTH).map { offset ->
            val date = since.plus(offset, DateTimeUnit.DAY)
            val (newWords, reviewed) = counts[date.toString()] ?: (0 to 0)
            DayActivity(date.toString(), date in activityDates, newWords, reviewed)
        }
    }

    private suspend fun vocabularyBreakdown(goalId: String): VocabularyBreakdown {
        val rows = db.vocabularyDao().wordStatusesByGoal(LOCAL_USER, goalId)
        val mastered = rows.count { it.status == VocabularySrs.STATUSES.last() }
        val notStarted = rows.count { it.status == VocabularySrs.STATUSES.first() }
        val inProgress = rows.size - mastered - notStarted
        val struggling = rows.count { it.incorrectStreak > 0 }
        return VocabularyBreakdown(notStarted, inProgress, mastered, struggling)
    }

    /** The in-progress word closest to "mastered" (highest STATUSES index) - ties broken by
     * whichever the query returns first, since exact tie-breaking isn't meaningful here. */
    private suspend fun nudgeWord(goalId: String): NudgeWord? {
        val candidates = db.vocabularyDao().inProgressWordsByGoal(LOCAL_USER, goalId)
        val closest = candidates.maxByOrNull { VocabularySrs.STATUSES.indexOf(it.status) } ?: return null
        val reviewsRemaining = VocabularySrs.STATUSES.lastIndex - VocabularySrs.STATUSES.indexOf(closest.status)
        return NudgeWord(closest.term, reviewsRemaining)
    }

    private fun autoTitle(goalText: String): String {
        val words = goalText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
        return "My $words Journey".take(60)
    }
}
