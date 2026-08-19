package evola.composeapp.wizard

import evola.composeapp.materials.StagedResource
import evola.shared.core.EvolaLog
import evola.shared.materials.ImageInput
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import kotlinx.coroutines.CancellationException
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce

/** The AI Analysis Wizard's 4-step state machine. "Start Analysis" (step 4) is the single point
 * where the staged file/text from the Add Resource screen finally gets uploaded, now bundled with
 * resource_type/organization_mode/ai_instructions. */
class AiWizardContainer(
    private val goalId: String,
    private val staged: StagedResource,
    private val repository: MaterialsRepository,
) : Container<WizardState, WizardIntent, Nothing> {

    override val store = store(initial = WizardState(stagedTitle = staged.title)) {
        configure { name = "AiWizardStore" }
        reduce { intent ->
            when (intent) {
                is WizardIntent.SelectResourceType -> updateState { copy(resourceType = intent.type) }
                is WizardIntent.SelectOrganizationMode -> {
                    if (intent.mode != OrganizationMode.MANUAL) updateState { copy(organizationMode = intent.mode) }
                }
                is WizardIntent.UpdateInstructions -> updateState { copy(aiInstructions = intent.text) }
                is WizardIntent.AppendSuggestion -> updateState {
                    copy(aiInstructions = if (aiInstructions.isBlank()) intent.suggestion else "$aiInstructions ${intent.suggestion}")
                }
                WizardIntent.GoNext -> updateState {
                    val index = STEP_ORDER.indexOf(step)
                    if (index < STEP_ORDER.lastIndex) copy(step = STEP_ORDER[index + 1]) else this
                }
                WizardIntent.GoBack -> updateState {
                    val index = STEP_ORDER.indexOf(step)
                    if (index > 0) copy(step = STEP_ORDER[index - 1]) else this
                }
                WizardIntent.DismissDuplicatePrompt -> updateState { copy(submitState = WizardSubmitState.Idle) }
                // `withState { }` (plain, from StateReceiver) reads the current WizardState safely
                // as `this` for the duration of this block - the sanctioned alternative to the
                // blocked `states.value` sync accessor (see plan gotcha notes).
                WizardIntent.StartAnalysis -> {
                    updateState { copy(submitState = WizardSubmitState.Submitting) }
                    withState {
                        val instructions = aiInstructions.trim().ifEmpty { null }
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
                                is UploadResult.Success -> updateState {
                                    copy(submitState = WizardSubmitState.Idle, materialCreated = MaterialCreatedEvent(result.materialId))
                                }
                                is UploadResult.DuplicateFile -> updateState { copy(submitState = WizardSubmitState.Duplicate(result.existingMaterialId)) }
                                UploadResult.UnsupportedFileType -> updateState {
                                    copy(submitState = WizardSubmitState.Error("Only PDF and DOCX files are supported."))
                                }
                                UploadResult.FileTooLarge -> updateState { copy(submitState = WizardSubmitState.Error("Content is too large.")) }
                                UploadResult.PasswordProtected -> updateState {
                                    copy(submitState = WizardSubmitState.Error("This PDF is password-protected. Remove the password and try again."))
                                }
                                UploadResult.CorruptedFile -> updateState {
                                    copy(submitState = WizardSubmitState.Error("This file couldn't be read. It may be corrupted."))
                                }
                                UploadResult.NoExtractableText -> updateState {
                                    copy(submitState = WizardSubmitState.Error("There isn't enough text here yet - add more content."))
                                }
                                UploadResult.GoalNotFound -> updateState {
                                    copy(submitState = WizardSubmitState.Error("Something went wrong. Please try again."))
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            EvolaLog.d("ai-wizard", "StartAnalysis failed: $e")
                            updateState { copy(submitState = WizardSubmitState.Error("Upload failed. Please try again.")) }
                        }
                    }
                }
            }
        }
    }
}
