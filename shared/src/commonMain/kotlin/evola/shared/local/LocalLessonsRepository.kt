package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.db.EvolaDatabase
import evola.shared.lessons.LessonDetail
import evola.shared.lessons.LessonSection
import evola.shared.lessons.LessonsRepository

/** On-device Lesson Details hub — ports the retired server `MaterialService.getLessonDetail`. Only
 * Vocabulary and Grammar are real; every other section is honestly locked. */
class LocalLessonsRepository(private val db: EvolaDatabase) : LessonsRepository {

    override suspend fun getLessonDetail(lessonId: String): ApiResult<LessonDetail> {
        val lesson = db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Lesson not found"))
        val material = db.materialsQueries.selectById(lesson.material_id).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))

        val vocabCount = db.vocabItemCount(lessonId)
        val vocabProgress = db.lessonVocabProgress(lessonId)
        val vocabState = if (vocabCount > 0 && vocabProgress >= 1f) "done" else "open"

        val grammarCount = db.grammarTopicCount(lessonId)
        val grammarProgress = db.lessonGrammarProgress(lessonId)
        val grammarJobStatus = db.grammarQueries.jobByLesson(lessonId).executeAsOneOrNull()?.status
        val (grammarSubtitle, grammarState) = when {
            grammarJobStatus != "DONE" -> "Still preparing…" to "open"
            grammarCount == 0 -> "No grammar topics" to "open"
            grammarProgress >= 1f -> "$grammarCount topics" to "done"
            else -> "$grammarCount topics" to "open"
        }

        val completionPercent = if (grammarCount == 0) vocabProgress else (vocabProgress + grammarProgress) / 2f

        val detail = LessonDetail(
            lessonId = lesson.id,
            number = lesson.number.toInt(),
            title = lesson.title,
            status = lesson.status,
            breadcrumb = material.filename,
            progressPercent = (completionPercent * 100).toInt(),
            sections = listOf(
                LessonSection("vocabulary", "Vocabulary", "$vocabCount words", locked = false, state = vocabState),
                LessonSection("grammar", "Grammar", grammarSubtitle, locked = false, state = grammarState),
                LessonSection("reading", "Reading", "Coming soon", locked = true, state = "locked"),
                LessonSection("exercises", "Exercises", "Coming soon", locked = true, state = "locked"),
                LessonSection("speaking", "Speaking", "Coming soon", locked = true, state = "locked"),
                LessonSection("writing", "Writing", "Coming soon", locked = true, state = "locked"),
                LessonSection("review", "Review", "Coming soon", locked = true, state = "locked"),
                LessonSection("progress", "Progress", "Coming soon", locked = true, state = "locked"),
            ),
        )
        return ApiResult.Success(detail)
    }
}
