package evola.shared.local

import evola.shared.achievements.AchievementsRepository
import evola.shared.core.common.ApiResult
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.grammarTopicCount
import evola.shared.core.common.lessonGrammarProgress
import evola.shared.core.common.lessonVocabProgress
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.db.EvolaDatabase
import evola.shared.goals.CreateGoalResult
import evola.shared.goals.DayActivity
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.GoalsRepository
import evola.shared.goals.Lesson
import evola.shared.goals.NudgeWord
import evola.shared.goals.UpdateGoalResult
import evola.shared.goals.VocabularyBreakdown
import evola.shared.language.NativeLanguage
import evola.shared.core.common.srs.computeBestStreak
import evola.shared.core.common.srs.computeStreak
import evola.shared.vocabulary.VocabularySrs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val WEEK_LENGTH = 7

/** Fully local goals/lessons/progress over SQLDelight — no server. Implements the same
 * [GoalsRepository] the ViewModels already use, so nothing above it changes. Progress computation
 * mirrors the retired server `GoalService`. */
class LocalGoalsRepository(
    private val db: EvolaDatabase,
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
        db.goalsQueries.deactivateAll(LOCAL_USER)
        val id = newId()
        db.goalsQueries.insert(id, LOCAL_USER, text, resolvedTitle, nativeLanguage.code, 1L, now, now)
        return CreateGoalResult.Success(Goal(id, text, resolvedTitle, nativeLanguage, isActive = true, createdAt = now.toString()))
    }

    override suspend fun updateGoal(goalId: String, goalText: String?, title: String?, nativeLanguage: NativeLanguage?): UpdateGoalResult {
        val existing = db.goalsQueries.selectById(goalId).executeAsOneOrNull()
            ?: run {
                EvolaLog.d("goals", "updateGoal: not found goalId=$goalId")
                return UpdateGoalResult.NotFound
            }
        val newText = goalText?.trim() ?: existing.goal_text
        if (newText.length !in 3..200) {
            EvolaLog.d("goals", "updateGoal validation failed: goalId=$goalId length=${newText.length}")
            return UpdateGoalResult.ValidationError("Goal text must be 3-200 characters.")
        }
        val newTitle = title?.trim()?.ifBlank { null } ?: existing.title
        val newNativeLanguage = nativeLanguage ?: NativeLanguage.fromCode(existing.native_language)
        db.goalsQueries.update(newText, newTitle, newNativeLanguage.code, nowMillis(), goalId)
        return UpdateGoalResult.Success(Goal(goalId, newText, newTitle, newNativeLanguage, existing.is_active == 1L, existing.created_at.toString()))
    }

    override suspend fun getActiveGoal(): ApiResult<Goal?> {
        val row = db.goalsQueries.selectActive(LOCAL_USER).executeAsOneOrNull()
        return ApiResult.Success(
            row?.let { Goal(it.id, it.goal_text, it.title, NativeLanguage.fromCode(it.native_language), it.is_active == 1L, it.created_at.toString()) },
        )
    }

    override suspend fun listLessons(goalId: String): ApiResult<List<Lesson>> {
        val lessons = db.lessonsQueries.selectByGoal(goalId).executeAsList().map { row ->
            Lesson(
                id = row.id,
                number = row.number.toInt(),
                title = row.title,
                status = row.status,
                vocabProgress = db.lessonVocabProgress(row.id),
                grammarProgress = db.lessonGrammarProgress(row.id),
                grammarCount = db.grammarTopicCount(row.id),
            )
        }
        return ApiResult.Success(lessons)
    }

    override suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress> {
        val lessons = (listLessons(goalId) as ApiResult.Success).data
        val overall = if (lessons.isEmpty()) 0f else lessons.map { it.completionPct }.average().toFloat()
        val currentLessonId = lessons.firstOrNull { it.completionPct < 1f }?.id

        val today = runCatching { LocalDate.parse(localDate) }.getOrNull()
        val rawActivityDates = db.activityQueries.completedDates(LOCAL_USER).executeAsList()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        if (today != null) maybeApplyStreakFreeze(today, rawActivityDates)
        val frozenDates = db.activityQueries.frozenDates(LOCAL_USER).executeAsList()
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
        val masteredCount = db.vocabularyQueries.masteredCountForUser(LOCAL_USER).executeAsOne().toInt()
        val newlyUnlockedBadges = (achievementsRepository.checkAndUnlock(masteredCount, streak) as? ApiResult.Success)?.data ?: emptyList()
        val wordsToReviewCount = db.vocabularyQueries.dueCountForGoal(LOCAL_USER, goalId, nowMillis()).executeAsOne().toInt()

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
        val alreadyFrozen = db.activityQueries.frozenDates(LOCAL_USER).executeAsList().contains(yesterday.toString())
        if (alreadyFrozen) return
        val available = settingsRepository.current().streakFreezesAvailable
        if (available <= 0) return
        db.activityQueries.insertFreeze(newId(), LOCAL_USER, yesterday.toString())
        settingsRepository.setStreakFreezesAvailable(available - 1)
    }

    /** The last [WEEK_LENGTH] calendar days (oldest first, [today] last), for Home's weekly streak
     * strip + stacked activity chart. [hadActivity] comes from the same [activityDates] set
     * [computeStreak] uses; per-day new/review counts come from `vocabulary_sessions.dailyCounts`,
     * grouped by each session's own stored local date (see Vocabulary.sq). A day with zero sessions
     * simply doesn't appear in the query result, so it's defaulted to 0/0 below. */
    private fun weeklyActivity(today: LocalDate, activityDates: Set<LocalDate>): List<DayActivity> {
        val since = today.minus(WEEK_LENGTH - 1, DateTimeUnit.DAY)
        val counts = db.vocabularyQueries.dailyCounts(LOCAL_USER, since.toString()).executeAsList()
            .associateBy({ it.local_date }, { (it.new_words ?: 0L).toInt() to (it.review_words ?: 0L).toInt() })
        return (0 until WEEK_LENGTH).map { offset ->
            val date = since.plus(offset, DateTimeUnit.DAY)
            val (newWords, reviewed) = counts[date.toString()] ?: (0 to 0)
            DayActivity(date.toString(), date in activityDates, newWords, reviewed)
        }
    }

    private fun vocabularyBreakdown(goalId: String): VocabularyBreakdown {
        val rows = db.vocabularyQueries.wordStatusesByGoal(LOCAL_USER, goalId).executeAsList()
        val mastered = rows.count { it.status == VocabularySrs.STATUSES.last() }
        val notStarted = rows.count { it.status == VocabularySrs.STATUSES.first() }
        val inProgress = rows.size - mastered - notStarted
        val struggling = rows.count { it.incorrect_streak > 0 }
        return VocabularyBreakdown(notStarted, inProgress, mastered, struggling)
    }

    /** The in-progress word closest to "mastered" (highest STATUSES index) - ties broken by
     * whichever the query returns first, since exact tie-breaking isn't meaningful here. */
    private fun nudgeWord(goalId: String): NudgeWord? {
        val candidates = db.vocabularyQueries.inProgressWordsByGoal(LOCAL_USER, goalId).executeAsList()
        val closest = candidates.maxByOrNull { VocabularySrs.STATUSES.indexOf(it.status) } ?: return null
        val reviewsRemaining = VocabularySrs.STATUSES.lastIndex - VocabularySrs.STATUSES.indexOf(closest.status)
        return NudgeWord(closest.term, reviewsRemaining)
    }

    private fun autoTitle(goalText: String): String {
        val words = goalText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
        return "My $words Journey".take(60)
    }
}
