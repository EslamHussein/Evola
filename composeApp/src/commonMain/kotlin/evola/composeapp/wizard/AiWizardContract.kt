package evola.composeapp.wizard

import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

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

/** Step 2's options - ENTIRE/AUTO/PAGES are real and backend-supported. MANUAL renders its full
 * card per the design but stays disabled - no manual-lesson-range backend exists. */
enum class OrganizationMode(val label: String, val wireValue: String) {
    ENTIRE("Analyze entire document", "entire"),
    AUTO("Automatically detect lessons", "auto"),
    PAGES("Split by page (no AI, skips contents/index pages)", "pages"),
    MANUAL("I will define lessons manually", "manual"),
}

val SUGGESTED_INSTRUCTIONS = listOf(
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

val STEP_ORDER = listOf(WizardStep.RESOURCE_INFO, WizardStep.ORGANIZATION, WizardStep.FOCUS, WizardStep.INSTRUCTIONS)

/** See [evola.composeapp.main.GoalUpdateEvent] - same state-based one-shot-event pattern
 * (`subscribeConsume`/`MVIAction` isn't visible from `commonMain` in FlowMVI 3.1.0). */
data class MaterialCreatedEvent(val materialId: String, val id: Long = kotlin.random.Random.nextLong())

/** The AI Analysis Wizard's 4-step state machine, consolidated into one [MVIState] - it's one
 * cohesive step-driven screen (all 5 original StateFlows changed together as the user progressed),
 * not independent lifecycles, so a single `copy()`-based state is the correct FlowMVI shape. */
data class WizardState(
    val step: WizardStep = WizardStep.RESOURCE_INFO,
    val resourceType: ResourceInfoType = ResourceInfoType.BOOK,
    val organizationMode: OrganizationMode = OrganizationMode.AUTO,
    val aiInstructions: String = "",
    val submitState: WizardSubmitState = WizardSubmitState.Idle,
    val suggestedInstructions: List<String> = SUGGESTED_INSTRUCTIONS,
    val stagedTitle: String = "",
    val materialCreated: MaterialCreatedEvent? = null,
) : MVIState

sealed interface WizardIntent : MVIIntent {
    data class SelectResourceType(val type: ResourceInfoType) : WizardIntent
    data class SelectOrganizationMode(val mode: OrganizationMode) : WizardIntent
    data class UpdateInstructions(val text: String) : WizardIntent
    data class AppendSuggestion(val suggestion: String) : WizardIntent

    /** Unlike the pre-FlowMVI version, [GoBack] no longer reports "already at the first step" back
     * to the caller via a return value (`states.value` sync reads are unconditionally blocked in
     * FlowMVI 3.1.0 - see the plan's gotcha notes) - the screen already has `state.step` in scope
     * from what it's rendering, so it decides "exit the wizard vs. go back a step" itself by
     * comparing against [STEP_ORDER]'s first element before dispatching [GoBack] at all. */
    data object GoNext : WizardIntent
    data object GoBack : WizardIntent
    data object DismissDuplicatePrompt : WizardIntent
    data object StartAnalysis : WizardIntent
}
