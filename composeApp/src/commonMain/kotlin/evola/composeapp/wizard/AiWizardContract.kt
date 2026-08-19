package evola.composeapp.wizard

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

/** The AI Analysis Wizard's 4-step state machine, consolidated into one state class - it's one
 * cohesive step-driven screen (all 5 original StateFlows changed together as the user progressed),
 * not independent lifecycles, so a single `copy()`-based state is the correct shape. */
data class WizardState(
    val step: WizardStep = WizardStep.RESOURCE_INFO,
    val resourceType: ResourceInfoType = ResourceInfoType.BOOK,
    val organizationMode: OrganizationMode = OrganizationMode.AUTO,
    val aiInstructions: String = "",
    val submitState: WizardSubmitState = WizardSubmitState.Idle,
    val suggestedInstructions: List<String> = SUGGESTED_INSTRUCTIONS,
    val stagedTitle: String = "",
)

sealed interface WizardSideEffect {
    data class MaterialCreated(val materialId: String) : WizardSideEffect
}
