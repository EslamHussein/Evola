package evola.shared.materials

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface UploadResult {
    data class Success(val materialId: String, val status: MaterialStatus) : UploadResult
    data object GoalNotFound : UploadResult
    data object UnsupportedFileType : UploadResult
    data object FileTooLarge : UploadResult
    data object PasswordProtected : UploadResult
    data object CorruptedFile : UploadResult
    data object NoExtractableText : UploadResult
    data class DuplicateFile(val existingMaterialId: String) : UploadResult
}

interface MaterialsRepository {
    suspend fun upload(accessToken: String, goalId: String, fileName: String, mimeType: String, bytes: ByteArray): UploadResult
    suspend fun list(accessToken: String): List<Material>
    suspend fun get(accessToken: String, materialId: String): MaterialDetail
    suspend fun reprocess(accessToken: String, materialId: String): Boolean
}

@Serializable
private data class MaterialUploadWireResponse(@SerialName("material_id") val materialId: String, val status: String)

@Serializable
private data class DuplicateFileWireResponse(@SerialName("existing_material_id") val existingMaterialId: String)

@Serializable
private data class WireErrorBody(val code: String, val message: String)

@Serializable
private data class WireErrorResponse(val error: WireErrorBody)

class HttpMaterialsRepository(
    private val baseUrl: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
) : MaterialsRepository {

    override suspend fun upload(
        accessToken: String,
        goalId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): UploadResult {
        val response = httpClient.submitFormWithBinaryData(
            url = "$baseUrl/materials/upload",
            formData = formData {
                append("goal_id", goalId)
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    },
                )
            },
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        return when (response.status) {
            HttpStatusCode.Accepted -> {
                val body = response.body<MaterialUploadWireResponse>()
                UploadResult.Success(body.materialId, MaterialStatus.valueOf(body.status))
            }
            HttpStatusCode.NotFound -> UploadResult.GoalNotFound
            HttpStatusCode.Conflict -> UploadResult.DuplicateFile(response.body<DuplicateFileWireResponse>().existingMaterialId)
            HttpStatusCode.BadRequest -> when (response.errorBody().code) {
                "UNSUPPORTED_FILE_TYPE" -> UploadResult.UnsupportedFileType
                "FILE_TOO_LARGE" -> UploadResult.FileTooLarge
                "PASSWORD_PROTECTED" -> UploadResult.PasswordProtected
                "CORRUPTED_FILE" -> UploadResult.CorruptedFile
                "NO_EXTRACTABLE_TEXT" -> UploadResult.NoExtractableText
                else -> error("Upload failed: HTTP ${response.status.value}")
            }
            else -> error("Upload failed: HTTP ${response.status.value}")
        }
    }

    override suspend fun list(accessToken: String): List<Material> =
        httpClient.get("$baseUrl/materials") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()

    override suspend fun get(accessToken: String, materialId: String): MaterialDetail =
        httpClient.get("$baseUrl/materials/$materialId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()

    override suspend fun reprocess(accessToken: String, materialId: String): Boolean {
        val response = httpClient.post("$baseUrl/materials/$materialId/reprocess") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.status == HttpStatusCode.Accepted
    }

    private suspend fun HttpResponse.errorBody(): WireErrorBody = body<WireErrorResponse>().error
}
