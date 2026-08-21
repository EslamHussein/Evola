package evola.shared.feature.learning.data

import evola.database.AppDatabase
import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.lessonVocabProgressRoom
import evola.shared.core.common.vocabItemCountRoom
import evola.shared.feature.learning.domain.LessonDetail
import evola.shared.feature.learning.domain.LessonSection
import evola.shared.feature.learning.domain.LessonsRepository

/** On-device Lesson Details hub — ports the retired server `MaterialService.getLessonDetail`. Only
 * Vocabulary and Grammar are real; every other section is honestly locked. */
class LocalLessonsRepository(private val db: AppDatabase) : LessonsRepository {

    override suspend fun getLessonDetail(lessonId: String): ApiResult<LessonDetail> {
        val lesson = db.lessonDao().selectById(lessonId)
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        val material = db.materialDao().selectById(lesson.materialId)
            ?: return fail(404, "Material not found", "lessonId=$lessonId materialId=${lesson.materialId}")

        val vocabCount = db.vocabItemCountRoom(lessonId)
        val vocabProgress = db.lessonVocabProgressRoom(lessonId)
        val vocabState = if (vocabCount > 0 && vocabProgress >= 1f) "done" else "open"

        // Vocabulary-only scope for now: Grammar and every other section are locked ("coming soon").
        // Progress is therefore vocabulary progress alone.
        val completionPercent = vocabProgress

        val detail = LessonDetail(
            lessonId = lesson.id,
            number = lesson.number.toInt(),
            title = lesson.title,
            status = lesson.status,
            breadcrumb = material.filename,
            progressPercent = (completionPercent * 100).toInt(),
            sections = listOf(
                LessonSection("vocabulary", "Vocabulary", "$vocabCount words", locked = false, state = vocabState),
                LessonSection("grammar", "Grammar", "Coming soon", locked = true, state = "locked"),
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

    private fun fail(code: Int, message: String, context: String): ApiResult.Failure {
        EvolaLog.d("lessons", "$message ($context)")
        return ApiResult.Failure(DataError.Http(code, message))
    }
}
