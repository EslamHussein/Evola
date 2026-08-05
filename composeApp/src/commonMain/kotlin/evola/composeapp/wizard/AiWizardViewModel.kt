package evola.composeapp.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import evola.composeapp.materials.StagedResource
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WizardStep { RESOURCE_INFO, ORGANIZATION, FOCUS, INSTRUCTIONS }

/** Step 1's options - real, single-select, submitted as `resource_type` (persisted but not yet
 * prompt-interpolated - see Phase 2). */
enum class ResourceInfoType(val label: String, val wireValue: String) {
    BOOK("Book", "book"),
    WORKBOOK("Workbook", "workbook"),
    TEACHER_NOTES("Teacher Notes", "teacher_notes"),
    EXAM_MATERIAL("Exam Material", "exam_material"),
    ARTICLE("Article", "article"),
}

/** Step 2's options - only ENTIRE/AUTO are real and backend-supported (Phase 2). MANUAL renders
 * its full card per the design but stays disabled - no manual-lesson-range backend exists. */
enum class OrganizationMode(val label: String, val wireValue: String) {
    ENTIRE("Analyze entire document", "entire"),
    AUTO("Automatically detect lessons", "auto"),
    MANUAL("I will define lessons manually", "manual"),
}

private val SUGGESTED_INSTRUCTIONS = listOf(
    "Focus on vocabulary useful for everyday conversation",
    "Skip proper nouns and place names",
    "Prioritize words related to my exam topic",
)

sealed interface WizardSubmitState {
    data object Idle : WizardSubmitState
    data object Submitting : WizardSubmitState
    data class Error(val message: String) : WizardSubmitState
    data class Duplicate(val existingMaterialId: String) : WizardSubmitState
}

private val STEP_ORDER = listOf(WizardStep.RESOURCE_INFO, WizardStep.ORGANIZATION, WizardStep.FOCUS, WizardStep.INSTRUCTIONS)

/** The AI Analysis Wizard's 4-step state machine. "Start Analysis" (step 4) is the single point
 * where the staged file/text from the Add Resource screen finally gets uploaded, now bundled with
 * resource_type/organization_mode/ai_instructions. */
class AiWizardViewModel(
    private val goalId: String,
    private val staged: StagedResource,
    private val repository: MaterialsRepository,
) : ViewModel() {

    private val _step = MutableStateFlow(WizardStep.RESOURCE_INFO)
    val step: StateFlow<WizardStep> = _step.asStateFlow()

    private val _resourceType = MutableStateFlow(ResourceInfoType.BOOK)
    val resourceType: StateFlow<ResourceInfoType> = _resourceType.asStateFlow()

    private val _organizationMode = MutableStateFlow(OrganizationMode.AUTO)
    val organizationMode: StateFlow<OrganizationMode> = _organizationMode.asStateFlow()

    private val _aiInstructions = MutableStateFlow("")
    val aiInstructions: StateFlow<String> = _aiInstructions.asStateFlow()

    private val _submitState = MutableStateFlow<WizardSubmitState>(WizardSubmitState.Idle)
    val submitState: StateFlow<WizardSubmitState> = _submitState.asStateFlow()

    val suggestedInstructions: List<String> = SUGGESTED_INSTRUCTIONS
    val stagedTitle: String = staged.title

    fun selectResourceType(type: ResourceInfoType) {
        _resourceType.value = type
    }

    fun selectOrganizationMode(mode: OrganizationMode) {
        if (mode == OrganizationMode.MANUAL) return
        _organizationMode.value = mode
    }

    fun updateAiInstructions(text: String) {
        _aiInstructions.value = text
    }

    fun appendSuggestion(suggestion: String) {
        _aiInstructions.value = if (_aiInstructions.value.isBlank()) suggestion else "${_aiInstructions.value} $suggestion"
    }

    fun goNext() {
        val index = STEP_ORDER.indexOf(_step.value)
        if (index < STEP_ORDER.lastIndex) _step.value = STEP_ORDER[index + 1]
    }

    /** Returns false when already on the first step, so the caller knows to exit the wizard instead. */
    fun goBack(): Boolean {
        val index = STEP_ORDER.indexOf(_step.value)
        if (index == 0) return false
        _step.value = STEP_ORDER[index - 1]
        return true
    }

    fun dismissDuplicatePrompt() {
        _submitState.value = WizardSubmitState.Idle
    }

    fun startAnalysis(onCreated: (materialId: String) -> Unit) {
        viewModelScope.launch {
            _submitState.value = WizardSubmitState.Submitting
            try {
                val instructions = _aiInstructions.value.trim().ifEmpty { null }
                val result = when (val resource = staged) {
                    is StagedResource.File -> repository.upload(
                        goalId, resource.fileName, resource.mimeType, resource.bytes,
                        organizationMode = _organizationMode.value.wireValue,
                        aiInstructions = instructions,
                        resourceType = _resourceType.value.wireValue,
                    )
                    is StagedResource.Text -> repository.uploadText(
                        goalId, resource.title, resource.text,
                        organizationMode = _organizationMode.value.wireValue,
                        aiInstructions = instructions,
                        resourceType = _resourceType.value.wireValue,
                    )
                }
                when (result) {
                    is UploadResult.Success -> {
                        _submitState.value = WizardSubmitState.Idle
                        onCreated(result.materialId)
                    }
                    is UploadResult.DuplicateFile -> _submitState.value = WizardSubmitState.Duplicate(result.existingMaterialId)
                    UploadResult.UnsupportedFileType -> _submitState.value =
                        WizardSubmitState.Error("Only PDF and DOCX files are supported.")
                    UploadResult.FileTooLarge -> _submitState.value = WizardSubmitState.Error("Content is too large.")
                    UploadResult.PasswordProtected -> _submitState.value =
                        WizardSubmitState.Error("This PDF is password-protected. Remove the password and try again.")
                    UploadResult.CorruptedFile -> _submitState.value =
                        WizardSubmitState.Error("This file couldn't be read. It may be corrupted.")
                    UploadResult.NoExtractableText -> _submitState.value =
                        WizardSubmitState.Error("There isn't enough text here yet - add more content.")
                    UploadResult.GoalNotFound -> _submitState.value = WizardSubmitState.Error("Something went wrong. Please try again.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _submitState.value = WizardSubmitState.Error(e.message ?: "Upload failed. Please try again.")
            }
        }
    }
}
