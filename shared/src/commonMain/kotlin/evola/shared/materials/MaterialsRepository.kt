package evola.shared.materials

import evola.shared.core.ApiResult

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
