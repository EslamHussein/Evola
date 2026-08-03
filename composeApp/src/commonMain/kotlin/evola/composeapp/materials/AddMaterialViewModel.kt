package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The Add Resource redesign's 2-option type grid - Image/Website/Notes stay out of scope,
 * matching the design handoff. */
enum class ResourceType { PDF, TEXT }

/** Material Upload rebuild (01_PRODUCT_SPEC.md §1.5) + the design-handoff's Text-paste ingestion -
 * uploads a real picked file or pasted text, surfacing every server-side rejection reason
 * distinctly rather than one generic "upload failed" message. */
class AddMaterialViewModel(
    private val accessToken: String,
    private val goalId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _selectedType = MutableStateFlow(ResourceType.PDF)
    val selectedType: StateFlow<ResourceType> = _selectedType.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _duplicateMaterialId = MutableStateFlow<String?>(null)
    val duplicateMaterialId: StateFlow<String?> = _duplicateMaterialId.asStateFlow()

    fun selectType(type: ResourceType) {
        _selectedType.value = type
    }

    fun submit(file: PickedFile, onUploaded: (materialId: String) -> Unit) {
        viewModelScope.launch {
            runSubmit(onUploaded) { repository.upload(accessToken, goalId, file.fileName, file.mimeType, file.bytes) }
        }
    }

    fun submitText(text: String, title: String, onUploaded: (materialId: String) -> Unit) {
        viewModelScope.launch {
            runSubmit(onUploaded) { repository.uploadText(accessToken, goalId, title, text) }
        }
    }

    private suspend fun runSubmit(onUploaded: (materialId: String) -> Unit, upload: suspend () -> UploadResult) {
        _isSubmitting.value = true
        _error.value = null
        _duplicateMaterialId.value = null
        try {
            when (val result = upload()) {
                is UploadResult.Success -> onUploaded(result.materialId)
                is UploadResult.DuplicateFile -> _duplicateMaterialId.value = result.existingMaterialId
                UploadResult.UnsupportedFileType -> _error.value = "Only PDF and DOCX files are supported."
                UploadResult.FileTooLarge -> _error.value = "Content is too large."
                UploadResult.PasswordProtected -> _error.value = "This PDF is password-protected. Remove the password and try again."
                UploadResult.CorruptedFile -> _error.value = "This file couldn't be read. It may be corrupted."
                UploadResult.NoExtractableText -> _error.value = "There isn't enough text here yet - add more content."
                UploadResult.GoalNotFound -> _error.value = "Something went wrong. Please try again."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Upload failed. Please try again."
        } finally {
            _isSubmitting.value = false
        }
    }

    fun dismissDuplicatePrompt() {
        _duplicateMaterialId.value = null
    }
}
