package evola.composeapp.materials

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Add Resource type grid - PDF, Text, and Image (photographed pages, transcribed on-device
 * via Claude vision - see [evola.shared.ai.ImageTranscriber]). Website/Notes stay out of scope,
 * matching the design handoff. */
enum class ResourceType { PDF, TEXT, IMAGE }

/** Add Resource screen state: picks a type and stages a file/pasted text - no network calls here.
 * The real upload happens at the AI Wizard's "Start Analysis" (see [StagedResource]). */
class AddMaterialViewModel : ViewModel() {

    private val _selectedType = MutableStateFlow(ResourceType.PDF)
    val selectedType: StateFlow<ResourceType> = _selectedType.asStateFlow()

    fun selectType(type: ResourceType) {
        _selectedType.value = type
    }
}
