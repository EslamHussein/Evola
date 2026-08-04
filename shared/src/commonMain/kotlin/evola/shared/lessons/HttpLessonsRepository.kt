package evola.shared.lessons

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : LessonsRepository {

    override suspend fun getLessonDetail(accessToken: String, lessonId: String): LessonDetail? {
        val response = httpClient.get("$baseUrl/lessons/$lessonId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.status != HttpStatusCode.OK) return null
        return response.body<LessonDetailWireResponse>().toDomain()
    }

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
