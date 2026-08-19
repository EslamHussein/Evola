package evola.shared.materials

import evola.shared.core.ApiResult

/** The shortest text [MaterialsRepository.uploadText] will accept - also the UI's own "Continue"
 * gate for pasted text, so the button's enabled state can never drift from what upload will
 * actually accept. */
const val MIN_EXTRACTABLE_TEXT_LENGTH = 20

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

/** One picked photo for the Add Resource "Image" material type - see [MaterialsRepository.uploadImages]. */
data class ImageInput(val fileName: String, val mimeType: String, val bytes: ByteArray)

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

    /** One or more photos (e.g. photographed book pages), transcribed on-device via
     * [evola.shared.ai.ImageTranscriber] and concatenated into one material - the resulting text
     * runs through the exact same segmentation/vocab/grammar pipeline as PDF/DOCX/pasted text. */
    suspend fun uploadImages(
        goalId: String,
        images: List<ImageInput>,
        organizationMode: String = "auto",
        aiInstructions: String? = null,
        resourceType: String? = null,
    ): UploadResult
    suspend fun list(): ApiResult<List<Material>>
    suspend fun get(materialId: String): ApiResult<MaterialDetail>
    suspend fun reprocess(materialId: String): ApiResult<Unit>

    /** Cascades to every lesson/vocabulary/grammar row under this material via the DB's own
     * foreign-key `ON DELETE CASCADE` (see the schema in `shared/.../sqldelight/.../db/`) - the
     * repository only ever deletes the one row. */
    suspend fun deleteMaterial(materialId: String): ApiResult<Unit>

    /** Cascades to that lesson's vocabulary/grammar rows the same way [deleteMaterial] does. */
    suspend fun deleteLesson(lessonId: String): ApiResult<Unit>
}
