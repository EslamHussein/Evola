package evola.composeapp.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.IconTile
import evola.composeapp.theme.components.RootTopBarTitle
import evola.composeapp.theme.components.SelectableChip
import evola.shared.goals.Goal
import evola.shared.language.NativeLanguage
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_profile_cancel
import evola.composeapp.generated.resources.main_profile_default_journey_title
import evola.composeapp.generated.resources.main_profile_edit_goal_cd
import evola.composeapp.generated.resources.main_profile_goal_field_label
import evola.composeapp.generated.resources.main_profile_goal_updated_snackbar
import evola.composeapp.generated.resources.main_profile_journey_title_field_label
import evola.composeapp.generated.resources.main_profile_language_badge
import evola.composeapp.generated.resources.main_profile_language_chip_label
import evola.composeapp.generated.resources.main_profile_native_language_label
import evola.composeapp.generated.resources.main_profile_progress_reset_failed_snackbar
import evola.composeapp.generated.resources.main_profile_progress_reset_snackbar
import evola.composeapp.generated.resources.main_profile_reset_all_confirm
import evola.composeapp.generated.resources.main_profile_reset_all_dialog_body
import evola.composeapp.generated.resources.main_profile_reset_all_dialog_title
import evola.composeapp.generated.resources.main_profile_save
import evola.composeapp.generated.resources.main_profile_saving
import evola.composeapp.generated.resources.main_profile_title
import evola.composeapp.generated.resources.main_profile_your_goal_label
import org.jetbrains.compose.resources.stringResource

/** Profile tab per 06_SCREENS_REFERENCE.md - account info + goal editing + sign-out.
 * Notifications/subscription/privacy are explicitly out of MVP scope (01_PRODUCT_SPEC.md), so
 * they aren't stubbed here at all rather than shown as fake placeholders.
 *
 * Redesigned as two "identity" cards (goal, AI key) that follow the same icon-tile + heading
 * language already used for lesson/material rows elsewhere in the app, rather than plain text
 * blocks - so Profile reads as part of the same design system instead of a bare settings form.
 *
 * The sections below the goal card live in sibling files in this package -
 * [ProfileAchievementsSection.kt], [ProfileAnthropicKeySection.kt], [ProfileAppSection.kt]
 * (also home to the shared `AppRow` row layout), [ProfileMiscSections.kt] (danger zone + credits)
 * - this file is just the screen shell plus the goal-editing card itself. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    goal: Goal,
    viewModel: ProfileViewModel,
    onGoalUpdated: (Goal) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val backupRepository = org.koin.compose.koinInject<evola.shared.local.BackupRepository>()
    var isEditingGoal by remember { mutableStateOf(false) }
    var goalText by remember(goal.id) { mutableStateOf(goal.goalText) }
    var title by remember(goal.id) { mutableStateOf(goal.title ?: "") }
    var nativeLanguage by remember(goal.id) { mutableStateOf(goal.nativeLanguage) }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val state by viewModel.subscribe()
    val isSubmitting = state.isSubmitting
    val errorMessage = state.errorMessage
    var showResetAllConfirm by remember { mutableStateOf(false) }
    val goalUpdatedMessage = stringResource(Res.string.main_profile_goal_updated_snackbar)
    val progressResetMessage = stringResource(Res.string.main_profile_progress_reset_snackbar)
    val progressResetFailedMessage = stringResource(Res.string.main_profile_progress_reset_failed_snackbar)
    LaunchedEffect(state.goalUpdated?.id) {
        state.goalUpdated?.let { event ->
            onGoalUpdated(event.goal)
            isEditingGoal = false
            snackbarHostState.showSnackbar(goalUpdatedMessage)
        }
    }
    LaunchedEffect(state.progressReset?.id) {
        state.progressReset?.let { event ->
            snackbarHostState.showSnackbar(if (event.success) progressResetMessage else progressResetFailedMessage)
        }
    }

    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(Res.string.main_profile_reset_all_dialog_title)) },
            text = { Text(stringResource(Res.string.main_profile_reset_all_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetAllConfirm = false
                    viewModel.intent(ProfileIntent.ResetAllProgress)
                }) { Text(stringResource(Res.string.main_profile_reset_all_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showResetAllConfirm = false }) { Text(stringResource(Res.string.main_profile_cancel)) } },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { RootTopBarTitle(stringResource(Res.string.main_profile_title)) }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusManager.clearFocus() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(EvolaSpacing.xl),
            ) {
                Text(
                    stringResource(Res.string.main_profile_your_goal_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(EvolaSpacing.sm))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
                        if (isEditingGoal) {
                            OutlinedTextField(
                                value = goalText,
                                onValueChange = { if (it.length <= 200) goalText = it },
                                label = { Text(stringResource(Res.string.main_profile_goal_field_label)) },
                                enabled = !isSubmitting,
                                minLines = 2,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(EvolaSpacing.sm))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 60) title = it },
                                label = { Text(stringResource(Res.string.main_profile_journey_title_field_label)) },
                                enabled = !isSubmitting,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(EvolaSpacing.md))
                            Text(stringResource(Res.string.main_profile_native_language_label), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(EvolaSpacing.xs))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                                NativeLanguage.entries.forEach { language ->
                                    SelectableChip(
                                        label = stringResource(Res.string.main_profile_language_chip_label, language.englishName, language.nativeName),
                                        selected = nativeLanguage == language,
                                        onClick = { nativeLanguage = language },
                                    )
                                }
                            }
                            errorMessage?.let {
                                Spacer(Modifier.height(EvolaSpacing.sm))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(EvolaSpacing.md))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm, Alignment.End),
                            ) {
                                TextButton(onClick = { isEditingGoal = false }, enabled = !isSubmitting) { Text(stringResource(Res.string.main_profile_cancel)) }
                                Button(
                                    onClick = {
                                        viewModel.intent(ProfileIntent.UpdateGoal(goal.id, goalText, title, nativeLanguage))
                                    },
                                    enabled = !isSubmitting && goalText.trim().length >= 3,
                                ) {
                                    Text(stringResource(if (isSubmitting) Res.string.main_profile_saving else Res.string.main_profile_save))
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                IconTile(Icons.Filled.Flag, locked = false)
                                Spacer(Modifier.width(EvolaSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.title ?: stringResource(Res.string.main_profile_default_journey_title), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(goal.goalText, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                                }
                                IconButton(onClick = { isEditingGoal = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.main_profile_edit_goal_cd), tint = EvolaColors.Text2)
                                }
                            }
                            Spacer(Modifier.height(EvolaSpacing.md))
                            LanguageBadge(goal.nativeLanguage)
                        }
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AchievementsSection(state.unlockedBadgeIds)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AnthropicKeySection(snackbarHostState, coroutineScope)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AppSection(onOpenSettings, backupRepository, state.progress, goal, snackbarHostState, coroutineScope)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                DangerZoneSection(onResetAllProgress = { showResetAllConfirm = true })

                Spacer(Modifier.height(EvolaSpacing.xxl))
                CreditsSection()
            }
        }
    }
}

/** Pill badge for the goal card's native-language readout - mirrors the icon+label chip language
 * used for lesson word-count/status tags elsewhere, instead of a plain gray caption. */
@Composable
private fun LanguageBadge(language: NativeLanguage) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
        Row(
            modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = EvolaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs),
        ) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = EvolaColors.Text2, modifier = Modifier.size(14.dp))
            Text(
                stringResource(Res.string.main_profile_language_badge, language.englishName, language.nativeName),
                style = MaterialTheme.typography.labelSmall,
                color = EvolaColors.Text2,
            )
        }
    }
}
