package evola.shared.lessons

import evola.shared.core.ApiResult

/** One row in the Lesson Details hub (Phase 6). Only "vocabulary" is ever unlocked today; every
 * other section is honestly locked until M7/M8 back it with real content. */
data class LessonSection(
    val key: String,
    val label: String,
    val subtitle: String,
    val locked: Boolean,
    val state: String,
)

/** Unified lesson-details destination reached from either the Materials tab (via Resource
 * Details) or the Study tab (via the flat lesson list) - fetched fresh by lessonId so both entry
 * points land on identical data regardless of which domain model (`materials.Lesson` vs
 * `goals.Lesson`) they arrived from. */
data class LessonDetail(
    val lessonId: String,
    val number: Int,
    val title: String,
    val status: String,
    val breadcrumb: String,
    val progressPercent: Int,
    val sections: List<LessonSection>,
)

interface LessonsRepository {
    suspend fun getLessonDetail(lessonId: String): ApiResult<LessonDetail>
}
