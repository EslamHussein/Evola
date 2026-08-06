@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.wizard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.BackHandler
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.ComingSoonChip
import evola.composeapp.theme.components.SegmentedProgressBar
import evola.composeapp.theme.components.SelectableChip

@Composable
fun AiWizardScreen(
    viewModel: AiWizardViewModel,
    onCancel: () -> Unit,
    onAnalysisStarted: (materialId: String) -> Unit,
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val resourceType by viewModel.resourceType.collectAsStateWithLifecycle()
    val organizationMode by viewModel.organizationMode.collectAsStateWithLifecycle()
    val aiInstructions by viewModel.aiInstructions.collectAsStateWithLifecycle()
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()

    val exitOrBack = { if (!viewModel.goBack()) onCancel() }
    BackHandler(onBack = exitOrBack)
    val focusManager = LocalFocusManager.current

    val stepIndex = WizardStep.entries.indexOf(step)
    val isSubmitting = submitState is WizardSubmitState.Submitting
    val errorMessage = (submitState as? WizardSubmitState.Error)?.message

    (submitState as? WizardSubmitState.Duplicate)?.let { duplicate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicatePrompt,
            title = { Text("Already uploaded") },
            text = { Text("You've already uploaded this content. Would you like to view it instead?") },
            confirmButton = {
                TextButton(onClick = { onAnalysisStarted(duplicate.existingMaterialId) }) { Text("View existing material") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicatePrompt) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.stagedTitle) },
                navigationIcon = {
                    IconButton(onClick = exitOrBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                                viewModel.startAnalysis(onAnalysisStarted)
                            } else {
                                viewModel.goNext()
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSubmitting) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Text("Starting...")
                            }
                        } else {
                            Text(if (step == WizardStep.INSTRUCTIONS) "Start Analysis" else "Continue")
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
                    WizardStep.RESOURCE_INFO -> ResourceInfoStep(resourceType, viewModel::selectResourceType)
                    WizardStep.ORGANIZATION -> OrganizationStep(organizationMode, viewModel::selectOrganizationMode)
                    WizardStep.FOCUS -> FocusStep()
                    WizardStep.INSTRUCTIONS -> InstructionsStep(
                        aiInstructions,
                        onChange = viewModel::updateAiInstructions,
                        suggestions = viewModel.suggestedInstructions,
                        onSuggestion = viewModel::appendSuggestion,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceInfoStep(selected: ResourceInfoType, onSelect: (ResourceInfoType) -> Unit) {
    Text("What kind of resource is this?", style = MaterialTheme.typography.titleMedium)
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
    Text("How should we organize this?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
        OrganizationCard(OrganizationMode.ENTIRE, selected, enabled = true, onSelect = onSelect)
        OrganizationCard(OrganizationMode.AUTO, selected, enabled = true, onSelect = onSelect)
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
    val isSelected = selected == mode
    Surface(
        onClick = { if (enabled) onSelect(mode) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) EvolaColors.GoldSoft else EvolaColors.Surface,
        border = BorderStroke(1.dp, if (isSelected) EvolaColors.Gold else EvolaColors.Border),
    ) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                if (isSelected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.size(20.dp))
                }
                Text(
                    mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) EvolaColors.Text else EvolaColors.Text3,
                )
            }
            if (mode == OrganizationMode.MANUAL) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                Spacer(Modifier.height(EvolaSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EvolaColors.Text3, modifier = Modifier.size(16.dp))
                    Text("Add lesson range", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                }
            }
        }
    }
}

@Composable
private fun FocusStep() {
    Text("What do you want to learn?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        SelectableChip(label = "Vocabulary", selected = true, onClick = {}, icon = Icons.AutoMirrored.Filled.MenuBook)
        ComingSoonChip(label = "Grammar", icon = Icons.AutoMirrored.Filled.Rule)
        ComingSoonChip(label = "Reading", icon = Icons.AutoMirrored.Filled.Article)
        ComingSoonChip(label = "Writing", icon = Icons.Filled.Edit)
        ComingSoonChip(label = "Speaking", icon = Icons.Filled.RecordVoiceOver)
        ComingSoonChip(label = "Listening", icon = Icons.Filled.Headphones)
    }
}

@Composable
private fun InstructionsStep(
    value: String,
    onChange: (String) -> Unit,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
) {
    Text("Anything specific you'd like the AI to focus on?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(140.dp),
        placeholder = { Text("Optional - e.g. \"Focus on food vocabulary\"") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
    )
    Spacer(Modifier.height(EvolaSpacing.md))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        suggestions.forEach { suggestion ->
            SelectableChip(label = suggestion, selected = false, onClick = { onSuggestion(suggestion) })
        }
    }
}
