package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.db.EvolaDatabase
import evola.shared.goals.CreateGoalResult
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.GoalsRepository
import evola.shared.goals.Lesson
import evola.shared.goals.UpdateGoalResult
import evola.shared.srs.MasterySrs
import evola.shared.srs.computeStreak
import kotlinx.datetime.LocalDate

/** Fully local goals/lessons/progress over SQLDelight — no server. Implements the same
 * [GoalsRepository] the ViewModels already use, so nothing above it changes. Progress computation
 * mirrors the retired server `GoalService`. */
class LocalGoalsRepository(private val db: EvolaDatabase) : GoalsRepository {

    override suspend fun createGoal(goalText: String, title: String?): CreateGoalResult {
        val text = goalText.trim()
        if (text.length !in 3..200) return CreateGoalResult.ValidationError("Goal text must be 3-200 characters.")
        val resolvedTitle = title?.trim()?.ifBlank { null } ?: autoTitle(text)
        val now = nowMillis()
        db.goalsQueries.deactivateAll(LOCAL_USER)
        val id = newId()
        db.goalsQueries.insert(id, LOCAL_USER, text, resolvedTitle, 1L, now, now)
        return CreateGoalResult.Success(Goal(id, text, resolvedTitle, isActive = true, createdAt = now.toString()))
    }

    override suspend fun updateGoal(goalId: String, goalText: String?, title: String?): UpdateGoalResult {
        val existing = db.goalsQueries.selectById(goalId).executeAsOneOrNull()
            ?: return UpdateGoalResult.NotFound
        val newText = goalText?.trim() ?: existing.goal_text
        if (newText.length !in 3..200) return UpdateGoalResult.ValidationError("Goal text must be 3-200 characters.")
        val newTitle = title?.trim()?.ifBlank { null } ?: existing.title
        db.goalsQueries.update(newText, newTitle, nowMillis(), goalId)
        return UpdateGoalResult.Success(Goal(goalId, newText, newTitle, existing.is_active == 1L, existing.created_at.toString()))
    }

    override suspend fun getActiveGoal(): ApiResult<Goal?> {
        val row = db.goalsQueries.selectActive(LOCAL_USER).executeAsOneOrNull()
        return ApiResult.Success(row?.let { Goal(it.id, it.goal_text, it.title, it.is_active == 1L, it.created_at.toString()) })
    }

    override suspend fun listLessons(goalId: String): ApiResult<List<Lesson>> {
        val lessons = db.lessonsQueries.selectByGoal(goalId).executeAsList().map { row ->
            val topicCount = db.grammarQueries.topicsByLesson(row.id).executeAsList().size
            Lesson(
                id = row.id,
                number = row.number.toInt(),
                title = row.title,
                status = row.status,
                vocabProgress = lessonVocabProgress(row.id),
                grammarProgress = lessonGrammarProgress(row.id),
                grammarCount = topicCount,
            )
        }
        return ApiResult.Success(lessons)
    }

    override suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress> {
        val lessons = (listLessons(goalId) as ApiResult.Success).data
        val overall = if (lessons.isEmpty()) 0f else lessons.map { it.completionPct }.average().toFloat()
        val currentLessonId = lessons.firstOrNull { it.completionPct < 1f }?.id

        val today = runCatching { LocalDate.parse(localDate) }.getOrNull()
        val activityDates = db.activityQueries.completedDates(LOCAL_USER).executeAsList()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val streak = if (today != null) computeStreak(activityDates, today) else 0
        val todayCompleted = today != null && today in activityDates

        return ApiResult.Success(GoalProgress(overall, currentLessonId, streak, todayCompleted))
    }

    private fun lessonVocabProgress(lessonId: String): Float {
        val items = db.vocabularyQueries.itemsByLesson(lessonId).executeAsList()
        if (items.isEmpty()) return 0f
        val stageIndices = items.mapNotNull { db.vocabularyQueries.progressForItem(LOCAL_USER, it.id).executeAsOneOrNull()?.mastery_state }
            .map { MasterySrs.STAGES.indexOf(it).coerceAtLeast(0) }
        return stageIndices.averageStageRatio()
    }

    private fun lessonGrammarProgress(lessonId: String): Float {
        val topics = db.grammarQueries.topicsByLesson(lessonId).executeAsList()
        if (topics.isEmpty()) return 0f
        val stageIndices = topics.mapNotNull { db.grammarQueries.progressForTopic(LOCAL_USER, it.id).executeAsOneOrNull()?.mastery_state }
            .map { MasterySrs.STAGES.indexOf(it).coerceAtLeast(0) }
        return stageIndices.averageStageRatio()
    }

    private fun List<Int>.averageStageRatio(): Float =
        if (isEmpty()) 0f else map { it / (MasterySrs.STAGES.size - 1f) }.average().toFloat()

    private fun autoTitle(goalText: String): String {
        val words = goalText.trim().split(Regex("\\s+")).take(5).joinToString(" ")
        return "My $words Journey".take(60)
    }
}
