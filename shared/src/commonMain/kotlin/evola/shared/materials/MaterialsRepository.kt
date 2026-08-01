package evola.shared.materials

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MaterialUploadResult(val materialId: String, val status: MaterialStatus, val cacheHit: Boolean)

interface MaterialsRepository {
    suspend fun upload(userId: String, filename: String, contentText: String): MaterialUploadResult
    suspend fun list(userId: String): List<Material>
    suspend fun get(materialId: String): MaterialDetail
}

class HttpMaterialsRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : MaterialsRepository {

    @Serializable
    private data class UploadWireRequest(val userId: String, val filename: String, val contentText: String)

    override suspend fun upload(userId: String, filename: String, contentText: String): MaterialUploadResult =
        httpClient.post("$baseUrl/api/materials") {
            contentType(ContentType.Application.Json)
            setBody(UploadWireRequest(userId, filename, contentText))
        }.body()

    override suspend fun list(userId: String): List<Material> =
        httpClient.get("$baseUrl/api/materials") {
            parameter("userId", userId)
        }.body()

    override suspend fun get(materialId: String): MaterialDetail =
        httpClient.get("$baseUrl/api/materials/$materialId").body()
}
