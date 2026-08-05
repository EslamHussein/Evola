package evola.shared.materials

import evola.shared.core.ApiResult
import evola.shared.core.map
import evola.shared.core.safeRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    // upload/uploadText keep the UploadResult sealed type — it models seven specific outcomes
    // (duplicate, password-protected, no extractable text, …) a generic ApiResult would flatten.
    suspend fun upload(
        goalId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        organizationMode: String = "auto",
        aiInstructions: String? = null,
        resourceType: String? = null,
    ): UploadResult
    suspend fun uploadText(
        goalId: String,
        fileName: String,
        text: String,
        organizationMode: String = "auto",
        aiInstructions: String? = null,
        resourceType: String? = null,
    ): UploadResult
    suspend fun list(): ApiResult<List<Material>>
    suspend fun get(materialId: String): ApiResult<MaterialDetail>
    suspend fun reprocess(materialId: String): ApiResult<Unit>
}

@Serializable
private data class MaterialUploadWireResponse(@SerialName("material_id") val materialId: String, val status: String)

@Serializable
private data class MaterialTextUploadWireRequest(
    @SerialName("goal_id") val goalId: String,
    @SerialName("file_name") val fileName: String,
    val text: String,
    @SerialName("organization_mode") val organizationMode: String,
    @SerialName("ai_instructions") val aiInstructions: String?,
    @SerialName("resource_type") val resourceType: String?,
)

@Serializable
private data class DuplicateFileWireResponse(@SerialName("existing_material_id") val existingMaterialId: String)

@Serializable
private data class WireErrorBody(val code: String, val message: String)

@Serializable
private data class WireErrorResponse(val error: WireErrorBody)

class HttpMaterialsRepository(
    private val client: HttpClient,
    private val baseUrl: String,
) : MaterialsRepository {

    override suspend fun upload(
        goalId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        val response = client.submitFormWithBinaryData(
            url = "$baseUrl/materials/upload",
            formData = formData {
                append("goal_id", goalId)
                append("organization_mode", organizationMode)
                aiInstructions?.let { append("ai_instructions", it) }
                resourceType?.let { append("resource_type", it) }
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    },
                )
            },
        )
        return response.toUploadResult()
    }

    override suspend fun uploadText(
        goalId: String,
        fileName: String,
        text: String,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        val response = client.post("$baseUrl/materials/upload-text") {
            contentType(ContentType.Application.Json)
            setBody(
                MaterialTextUploadWireRequest(
                    goalId = goalId,
                    fileName = fileName,
                    text = text,
                    organizationMode = organizationMode,
                    aiInstructions = aiInstructions,
                    resourceType = resourceType,
                ),
            )
        }
        return response.toUploadResult()
    }

    private suspend fun HttpResponse.toUploadResult(): UploadResult = when (status) {
        HttpStatusCode.Accepted -> {
            val body = body<MaterialUploadWireResponse>()
            UploadResult.Success(body.materialId, MaterialStatus.valueOf(body.status))
        }
        HttpStatusCode.NotFound -> UploadResult.GoalNotFound
        HttpStatusCode.Conflict -> UploadResult.DuplicateFile(body<DuplicateFileWireResponse>().existingMaterialId)
        HttpStatusCode.BadRequest -> when (errorBody().code) {
            "UNSUPPORTED_FILE_TYPE" -> UploadResult.UnsupportedFileType
            "FILE_TOO_LARGE" -> UploadResult.FileTooLarge
            "PASSWORD_PROTECTED" -> UploadResult.PasswordProtected
            "CORRUPTED_FILE" -> UploadResult.CorruptedFile
            "NO_EXTRACTABLE_TEXT" -> UploadResult.NoExtractableText
            else -> error("Upload failed: HTTP ${status.value}")
        }
        else -> error("Upload failed: HTTP ${status.value}")
    }

    override suspend fun list(): ApiResult<List<Material>> =
        safeRequest { client.get("$baseUrl/materials") }

    override suspend fun get(materialId: String): ApiResult<MaterialDetail> =
        safeRequest { client.get("$baseUrl/materials/$materialId") }

    override suspend fun reprocess(materialId: String): ApiResult<Unit> =
        safeRequest<String> { client.post("$baseUrl/materials/$materialId/reprocess") }.map { }

    private suspend fun HttpResponse.errorBody(): WireErrorBody = body<WireErrorResponse>().error
}
