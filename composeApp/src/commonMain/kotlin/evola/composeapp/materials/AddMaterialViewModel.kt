package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.shared.materials.MaterialsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddMaterialViewModel(
    private val userId: String,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun submit(filename: String, text: String, onUploaded: (materialId: String) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            try {
                val result = repository.upload(userId, filename, text)
                onUploaded(result.materialId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Upload failed. Please try again."
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
