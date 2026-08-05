package evola.shared.lessons

import evola.shared.core.ApiResult
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class LessonSectionWireResponse(
    val key: String,
    val label: String,
    val subtitle: String,
    val locked: Boolean,
    val state: String,
)

@Serializable
private data class LessonDetailWireResponse(
    @SerialName("lesson_id") val lessonId: String,
    val number: Int,
    val title: String,
    val status: String,
    val breadcrumb: String,
    @SerialName("progress_percent") val progressPercent: Int,
    val sections: List<LessonSectionWireResponse>,
)

class HttpLessonsRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : LessonsRepository {

    override suspend fun getLessonDetail(lessonId: String): ApiResult<LessonDetail> =
        safeRequest<LessonDetailWireResponse> {
            client.get("$baseUrl/lessons/$lessonId")
        }.map { it.toDomain() }

    private fun LessonDetailWireResponse.toDomain() = LessonDetail(
        lessonId = lessonId,
        number = number,
        title = title,
        status = status,
        breadcrumb = breadcrumb,
        progressPercent = progressPercent,
        sections = sections.map { LessonSection(it.key, it.label, it.subtitle, it.locked, it.state) },
    )
}
