@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.feature.materials.ui

import evola.composeapp.feature.materials.vm.AiWizardViewModel
import evola.composeapp.feature.materials.vm.OrganizationMode
import evola.composeapp.feature.materials.vm.ResourceInfoType
import evola.composeapp.feature.materials.vm.WizardSideEffect
import evola.composeapp.feature.materials.vm.WizardState
import evola.composeapp.feature.materials.vm.WizardStep
import evola.composeapp.feature.materials.vm.WizardSubmitState
import evola.composeapp.feature.materials.vm.STEP_ORDER
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import evola.composeapp.core.common.ChaseLoadingDots
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import evola.composeapp.core.navigation.BackHandler
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.wizard_add_lesson_range
import evola.composeapp.generated.resources.wizard_back
import evola.composeapp.generated.resources.wizard_cancel
import evola.composeapp.generated.resources.wizard_coming_soon
import evola.composeapp.generated.resources.wizard_continue
import evola.composeapp.generated.resources.wizard_duplicate_message
import evola.composeapp.generated.resources.wizard_duplicate_title
import evola.composeapp.generated.resources.wizard_focus_grammar
import evola.composeapp.generated.resources.wizard_focus_listening
import evola.composeapp.generated.resources.wizard_focus_prompt
import evola.composeapp.generated.resources.wizard_focus_reading
import evola.composeapp.generated.resources.wizard_focus_speaking
import evola.composeapp.generated.resources.wizard_focus_vocabulary
import evola.composeapp.generated.resources.wizard_focus_writing
import evola.composeapp.generated.resources.wizard_instructions_placeholder
import evola.composeapp.generated.resources.wizard_instructions_prompt
import evola.composeapp.generated.resources.wizard_organization_prompt
import evola.composeapp.generated.resources.wizard_resource_info_prompt
import evola.composeapp.generated.resources.wizard_start_analysis
import evola.composeapp.generated.resources.wizard_starting
import evola.composeapp.generated.resources.wizard_view_existing_material
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.ComingSoonChip
import evola.composeapp.core.designsystem.components.SegmentedProgressBar
import evola.composeapp.core.designsystem.components.SelectableChip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun AiWizardScreen(
    viewModel: AiWizardViewModel,
    onCancel: () -> Unit,
    onAnalysisStarted: (materialId: String) -> Unit,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is WizardSideEffect.MaterialCreated -> onAnalysisStarted(effect.materialId)
        }
    }

    AiWizardContent(
        state = state,
        onCancel = onCancel,
        onAnalysisStarted = onAnalysisStarted,
        onGoBack = { viewModel.goBack() },
        onGoNext = { viewModel.goNext() },
        onStartAnalysis = { viewModel.startAnalysis() },
        onDismissDuplicatePrompt = { viewModel.dismissDuplicatePrompt() },
        onSelectResourceType = { type -> viewModel.selectResourceType(type) },
        onSelectOrganizationMode = { mode -> viewModel.selectOrganizationMode(mode) },
        onUpdateInstructions = { text -> viewModel.updateInstructions(text) },
        onAppendSuggestion = { suggestion -> viewModel.appendSuggestion(suggestion) },
    )
}

@Composable
private fun AiWizardContent(
    state: WizardState,
    onCancel: () -> Unit,
    onAnalysisStarted: (materialId: String) -> Unit,
    onGoBack: () -> Unit,
    onGoNext: () -> Unit,
    onStartAnalysis: () -> Unit,
    onDismissDuplicatePrompt: () -> Unit,
    onSelectResourceType: (ResourceInfoType) -> Unit,
    onSelectOrganizationMode: (OrganizationMode) -> Unit,
    onUpdateInstructions: (String) -> Unit,
    onAppendSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    val resourceType = state.resourceType
    val organizationMode = state.organizationMode
    val aiInstructions = state.aiInstructions
    val submitState = state.submitState

    val exitOrBack: () -> Unit = {
        if (step == STEP_ORDER.first()) onCancel() else onGoBack()
    }
    BackHandler(onBack = exitOrBack)
    val focusManager = LocalFocusManager.current

    val stepIndex = WizardStep.entries.indexOf(step)
    val isSubmitting = submitState is WizardSubmitState.Submitting
    val errorMessage = (submitState as? WizardSubmitState.Error)?.message

    (submitState as? WizardSubmitState.Duplicate)?.let { duplicate ->
        AlertDialog(
            onDismissRequest = onDismissDuplicatePrompt,
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(Res.string.wizard_duplicate_title)) },
            text = { Text(stringResource(Res.string.wizard_duplicate_message)) },
            confirmButton = {
                TextButton(onClick = { onAnalysisStarted(duplicate.existingMaterialId) }) {
                    Text(stringResource(Res.string.wizard_view_existing_material))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDuplicatePrompt) {
                    Text(stringResource(Res.string.wizard_cancel))
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.stagedTitle) },
                navigationIcon = {
                    IconButton(onClick = exitOrBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.wizard_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(EvolaSpacing.sm))
                    }
                    Button(
                        onClick = {
                            if (step == WizardStep.INSTRUCTIONS) {
                                onStartAnalysis()
                            } else {
                                onGoNext()
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                                ChaseLoadingDots(size = 16.dp, color = LocalContentColor.current)
                                Text(stringResource(Res.string.wizard_starting))
                            }
                        } else {
                            Text(
                                stringResource(
                                    if (step == WizardStep.INSTRUCTIONS) Res.string.wizard_start_analysis else Res.string.wizard_continue,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusManager.clearFocus() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(EvolaSpacing.lg),
            ) {
                SegmentedProgressBar(segmentCount = WizardStep.entries.size, filledCount = stepIndex + 1)
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (step) {
                    WizardStep.RESOURCE_INFO -> ResourceInfoStep(resourceType, onSelectResourceType)
                    WizardStep.ORGANIZATION -> OrganizationStep(organizationMode, onSelectOrganizationMode)
                    WizardStep.FOCUS -> FocusStep()
                    WizardStep.INSTRUCTIONS -> InstructionsStep(
                        aiInstructions,
                        onChange = onUpdateInstructions,
                        suggestions = state.suggestedInstructions,
                        onSuggestion = onAppendSuggestion,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceInfoStep(selected: ResourceInfoType, onSelect: (ResourceInfoType) -> Unit) {
    Text(stringResource(Res.string.wizard_resource_info_prompt), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        val icons = mapOf(
            ResourceInfoType.BOOK to Icons.Filled.Book,
            ResourceInfoType.WORKBOOK to Icons.AutoMirrored.Filled.MenuBook,
            ResourceInfoType.TEACHER_NOTES to Icons.AutoMirrored.Filled.SpeakerNotes,
            ResourceInfoType.EXAM_MATERIAL to Icons.Filled.Quiz,
            ResourceInfoType.ARTICLE to Icons.AutoMirrored.Filled.Article,
        )
        ResourceInfoType.entries.forEach { type ->
            SelectableChip(
                label = type.label,
                selected = selected == type,
                onClick = { onSelect(type) },
                icon = icons[type],
            )
        }
    }
}

@Composable
private fun OrganizationStep(selected: OrganizationMode, onSelect: (OrganizationMode) -> Unit) {
    Text(stringResource(Res.string.wizard_organization_prompt), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
        OrganizationCard(OrganizationMode.ENTIRE, selected, enabled = true, onSelect = onSelect)
        OrganizationCard(OrganizationMode.AUTO, selected, enabled = true, onSelect = onSelect)
        OrganizationCard(OrganizationMode.PAGES, selected, enabled = true, onSelect = onSelect)
        OrganizationCard(OrganizationMode.MANUAL, selected, enabled = false, onSelect = onSelect)
    }
}

@Composable
private fun OrganizationCard(
    mode: OrganizationMode,
    selected: OrganizationMode,
    enabled: Boolean,
    onSelect: (OrganizationMode) -> Unit,
) {
    SelectableChip(
        label = mode.label,
        selected = selected == mode,
        onClick = { onSelect(mode) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        subtitle = if (mode == OrganizationMode.MANUAL) {
            "${stringResource(Res.string.wizard_coming_soon)}\n${stringResource(Res.string.wizard_add_lesson_range)}"
        } else {
            null
        },
    )
}

@Composable
private fun FocusStep() {
    Text(stringResource(Res.string.wizard_focus_prompt), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        SelectableChip(
            label = stringResource(Res.string.wizard_focus_vocabulary),
            selected = true,
            onClick = {},
            icon = Icons.AutoMirrored.Filled.MenuBook,
        )
        ComingSoonChip(label = stringResource(Res.string.wizard_focus_grammar), icon = Icons.AutoMirrored.Filled.Rule)
        ComingSoonChip(label = stringResource(Res.string.wizard_focus_reading), icon = Icons.AutoMirrored.Filled.Article)
        ComingSoonChip(label = stringResource(Res.string.wizard_focus_writing), icon = Icons.Filled.Edit)
        ComingSoonChip(label = stringResource(Res.string.wizard_focus_speaking), icon = Icons.Filled.RecordVoiceOver)
        ComingSoonChip(label = stringResource(Res.string.wizard_focus_listening), icon = Icons.Filled.Headphones)
    }
}

@Composable
private fun InstructionsStep(
    value: String,
    onChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
) {
    Text(stringResource(Res.string.wizard_instructions_prompt), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(140.dp),
        placeholder = { Text(stringResource(Res.string.wizard_instructions_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    )
    Spacer(Modifier.height(EvolaSpacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        suggestions.forEach { suggestion ->
            SelectableChip(label = suggestion, selected = false, onClick = { onSuggestion(suggestion) })
        }
    }
}

private val fakeWizardContentActions = object {
    val onCancel: () -> Unit = {}
    val onAnalysisStarted: (String) -> Unit = {}
    val onGoBack: () -> Unit = {}
    val onGoNext: () -> Unit = {}
    val onStartAnalysis: () -> Unit = {}
    val onDismissDuplicatePrompt: () -> Unit = {}
    val onSelectResourceType: (ResourceInfoType) -> Unit = {}
    val onSelectOrganizationMode: (OrganizationMode) -> Unit = {}
    val onUpdateInstructions: (String) -> Unit = {}
    val onAppendSuggestion: (String) -> Unit = {}
}

@Preview
@Composable
private fun AiWizardResourceInfoPreview() {
    val a = fakeWizardContentActions
    EvolaTheme {
        AiWizardContent(
            state = WizardState(step = WizardStep.RESOURCE_INFO, stagedTitle = "grammar-book.pdf"),
            onCancel = a.onCancel, onAnalysisStarted = a.onAnalysisStarted, onGoBack = a.onGoBack, onGoNext = a.onGoNext,
            onStartAnalysis = a.onStartAnalysis, onDismissDuplicatePrompt = a.onDismissDuplicatePrompt,
            onSelectResourceType = a.onSelectResourceType, onSelectOrganizationMode = a.onSelectOrganizationMode,
            onUpdateInstructions = a.onUpdateInstructions, onAppendSuggestion = a.onAppendSuggestion,
        )
    }
}

@Preview
@Composable
private fun AiWizardOrganizationPreview() {
    val a = fakeWizardContentActions
    EvolaTheme {
        AiWizardContent(
            state = WizardState(step = WizardStep.ORGANIZATION, stagedTitle = "grammar-book.pdf"),
            onCancel = a.onCancel, onAnalysisStarted = a.onAnalysisStarted, onGoBack = a.onGoBack, onGoNext = a.onGoNext,
            onStartAnalysis = a.onStartAnalysis, onDismissDuplicatePrompt = a.onDismissDuplicatePrompt,
            onSelectResourceType = a.onSelectResourceType, onSelectOrganizationMode = a.onSelectOrganizationMode,
            onUpdateInstructions = a.onUpdateInstructions, onAppendSuggestion = a.onAppendSuggestion,
        )
    }
}

@Preview
@Composable
private fun AiWizardInstructionsSubmittingPreview() {
    val a = fakeWizardContentActions
    EvolaTheme {
        AiWizardContent(
            state = WizardState(step = WizardStep.INSTRUCTIONS, stagedTitle = "grammar-book.pdf", submitState = WizardSubmitState.Submitting),
            onCancel = a.onCancel, onAnalysisStarted = a.onAnalysisStarted, onGoBack = a.onGoBack, onGoNext = a.onGoNext,
            onStartAnalysis = a.onStartAnalysis, onDismissDuplicatePrompt = a.onDismissDuplicatePrompt,
            onSelectResourceType = a.onSelectResourceType, onSelectOrganizationMode = a.onSelectOrganizationMode,
            onUpdateInstructions = a.onUpdateInstructions, onAppendSuggestion = a.onAppendSuggestion,
        )
    }
}

@Preview
@Composable
private fun AiWizardDuplicatePromptPreview() {
    val a = fakeWizardContentActions
    EvolaTheme {
        AiWizardContent(
            state = WizardState(
                step = WizardStep.INSTRUCTIONS, stagedTitle = "grammar-book.pdf",
                submitState = WizardSubmitState.Duplicate(existingMaterialId = "m1"),
            ),
            onCancel = a.onCancel, onAnalysisStarted = a.onAnalysisStarted, onGoBack = a.onGoBack, onGoNext = a.onGoNext,
            onStartAnalysis = a.onStartAnalysis, onDismissDuplicatePrompt = a.onDismissDuplicatePrompt,
            onSelectResourceType = a.onSelectResourceType, onSelectOrganizationMode = a.onSelectOrganizationMode,
            onUpdateInstructions = a.onUpdateInstructions, onAppendSuggestion = a.onAppendSuggestion,
        )
    }
}
