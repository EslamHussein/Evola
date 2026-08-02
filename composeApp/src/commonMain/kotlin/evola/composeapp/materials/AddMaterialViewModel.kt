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

/** Material Upload rebuild (01_PRODUCT_SPEC.md §1.5) - uploads a real picked file, surfacing every
 * server-side rejection reason distinctly rather than one generic "upload failed" message. */
class AddMaterialViewModel(
    private val accessToken: String,
    private val goalId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _duplicateMaterialId = MutableStateFlow<String?>(null)
    val duplicateMaterialId: StateFlow<String?> = _duplicateMaterialId.asStateFlow()

    fun submit(file: PickedFile, onUploaded: (materialId: String) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            _duplicateMaterialId.value = null
            try {
                when (val result = repository.upload(accessToken, goalId, file.fileName, file.mimeType, file.bytes)) {
                    is UploadResult.Success -> onUploaded(result.materialId)
                    is UploadResult.DuplicateFile -> _duplicateMaterialId.value = result.existingMaterialId
                    UploadResult.UnsupportedFileType -> _error.value = "Only PDF and DOCX files are supported."
                    UploadResult.FileTooLarge -> _error.value = "Files must be under 25MB."
                    UploadResult.PasswordProtected -> _error.value = "This PDF is password-protected. Remove the password and try again."
                    UploadResult.CorruptedFile -> _error.value = "This file couldn't be read. It may be corrupted."
                    UploadResult.NoExtractableText -> _error.value = "This looks like a scanned file. Upload a text-based PDF or DOCX."
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
    }

    fun dismissDuplicatePrompt() {
        _duplicateMaterialId.value = null
    }
}
