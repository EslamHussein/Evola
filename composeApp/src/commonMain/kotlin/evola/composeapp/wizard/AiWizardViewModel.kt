package evola.composeapp.wizard

import androidx.lifecycle.ViewModel
import evola.composeapp.materials.StagedResource
import evola.shared.core.EvolaLog
import evola.shared.materials.ImageInput
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** The AI Analysis Wizard's 4-step state machine. "Start Analysis" (step 4) is the single point
 * where the staged file/text from the Add Resource screen finally gets uploaded, now bundled with
 * resource_type/organization_mode/ai_instructions. */
class AiWizardViewModel(
    private val goalId: String,
    private val staged: StagedResource,
    private val repository: MaterialsRepository,
) : ViewModel(), OrbitContainerHost<WizardState, WizardState, WizardSideEffect> {

    override val container = orbitContainer<WizardState, WizardSideEffect>(WizardState(stagedTitle = staged.title))

    fun selectResourceType(type: ResourceInfoType) = intent {
        reduce { state.copy(resourceType = type) }
    }

    fun selectOrganizationMode(mode: OrganizationMode) = intent {
        if (mode != OrganizationMode.MANUAL) reduce { state.copy(organizationMode = mode) }
    }

    fun updateInstructions(text: String) = intent {
        reduce { state.copy(aiInstructions = text) }
    }

    fun appendSuggestion(suggestion: String) = intent {
        reduce {
            state.copy(aiInstructions = if (state.aiInstructions.isBlank()) suggestion else "${state.aiInstructions} $suggestion")
        }
    }

    fun goNext() = intent {
        reduce {
            val index = STEP_ORDER.indexOf(state.step)
            if (index < STEP_ORDER.lastIndex) state.copy(step = STEP_ORDER[index + 1]) else state
        }
    }

    /** Unlike the pre-FlowMVI version, [goBack] no longer reports "already at the first step" back
     * to the caller via a return value - the screen already has `state.step` in scope from what
     * it's rendering, so it decides "exit the wizard vs. go back a step" itself by comparing
     * against [STEP_ORDER]'s first element before calling this at all. */
    fun goBack() = intent {
        reduce {
            val index = STEP_ORDER.indexOf(state.step)
            if (index > 0) state.copy(step = STEP_ORDER[index - 1]) else state
        }
    }

    fun dismissDuplicatePrompt() = intent {
        reduce { state.copy(submitState = WizardSubmitState.Idle) }
    }

    fun startAnalysis() = intent {
        reduce { state.copy(submitState = WizardSubmitState.Submitting) }
        val instructions = state.aiInstructions.trim().ifEmpty { null }
        val organizationMode = state.organizationMode
        val resourceType = state.resourceType
        try {
            val result = when (val resource = staged) {
                is StagedResource.File -> repository.upload(
                    goalId, resource.fileName, resource.mimeType, resource.bytes,
                    organizationMode = organizationMode.wireValue,
                    aiInstructions = instructions,
                    resourceType = resourceType.wireValue,
                )
                is StagedResource.Text -> repository.uploadText(
                    goalId, resource.title, resource.text,
                    organizationMode = organizationMode.wireValue,
                    aiInstructions = instructions,
                    resourceType = resourceType.wireValue,
                )
                is StagedResource.Images -> repository.uploadImages(
                    goalId, resource.images.map { ImageInput(it.fileName, it.mimeType, it.bytes) },
                    organizationMode = organizationMode.wireValue,
                    aiInstructions = instructions,
                    resourceType = resourceType.wireValue,
                )
            }
            when (result) {
                is UploadResult.Success -> {
                    reduce { state.copy(submitState = WizardSubmitState.Idle) }
                    postSideEffect(WizardSideEffect.MaterialCreated(result.materialId))
                }
                is UploadResult.DuplicateFile -> reduce { state.copy(submitState = WizardSubmitState.Duplicate(result.existingMaterialId)) }
                UploadResult.UnsupportedFileType -> reduce {
                    state.copy(submitState = WizardSubmitState.Error("Only PDF and DOCX files are supported."))
                }
                UploadResult.FileTooLarge -> reduce { state.copy(submitState = WizardSubmitState.Error("Content is too large.")) }
                UploadResult.PasswordProtected -> reduce {
                    state.copy(submitState = WizardSubmitState.Error("This PDF is password-protected. Remove the password and try again."))
                }
                UploadResult.CorruptedFile -> reduce {
                    state.copy(submitState = WizardSubmitState.Error("This file couldn't be read. It may be corrupted."))
                }
                UploadResult.NoExtractableText -> reduce {
                    state.copy(submitState = WizardSubmitState.Error("There isn't enough text here yet - add more content."))
                }
                UploadResult.GoalNotFound -> reduce {
                    state.copy(submitState = WizardSubmitState.Error("Something went wrong. Please try again."))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EvolaLog.d("ai-wizard", "StartAnalysis failed: $e")
            reduce { state.copy(submitState = WizardSubmitState.Error("Upload failed. Please try again.")) }
        }
    }
}
