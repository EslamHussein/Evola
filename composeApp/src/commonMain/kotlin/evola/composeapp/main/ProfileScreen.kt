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
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.IconTile
import evola.composeapp.core.designsystem.components.RootTopBarTitle
import evola.composeapp.core.designsystem.components.SelectableChip
import evola.shared.core.common.ApiResult
import evola.shared.feature.onboarding.domain.Goal
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import evola.shared.language.NativeLanguage
import evola.shared.local.BackupRepository
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
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    val backupRepository = koinInject<BackupRepository>()
    var isEditingGoal by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.collectAsState()
    var showResetAllConfirm by remember { mutableStateOf(false) }
    val goalUpdatedMessage = stringResource(Res.string.main_profile_goal_updated_snackbar)
    val progressResetMessage = stringResource(Res.string.main_profile_progress_reset_snackbar)
    val progressResetFailedMessage = stringResource(Res.string.main_profile_progress_reset_failed_snackbar)
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ProfileSideEffect.GoalUpdated -> {
                onGoalUpdated(effect.goal)
                isEditingGoal = false
                snackbarHostState.showSnackbar(goalUpdatedMessage)
            }
            is ProfileSideEffect.ProgressReset -> {
                snackbarHostState.showSnackbar(if (effect.success) progressResetMessage else progressResetFailedMessage)
            }
        }
    }

    ProfileContent(
        goal = goal,
        state = state,
        isEditingGoal = isEditingGoal,
        onEditingGoalChange = { isEditingGoal = it },
        showResetAllConfirm = showResetAllConfirm,
        onShowResetAllConfirmChange = { showResetAllConfirm = it },
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        backupRepository = backupRepository,
        onOpenSettings = onOpenSettings,
        onUpdateGoal = { goalId, goalText, title, nativeLanguage -> viewModel.updateGoal(goalId, goalText, title, nativeLanguage) },
        onResetAllProgress = { viewModel.resetAllProgress() },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    goal: Goal,
    state: ProfileState,
    isEditingGoal: Boolean,
    onEditingGoalChange: (Boolean) -> Unit,
    showResetAllConfirm: Boolean,
    onShowResetAllConfirmChange: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    backupRepository: BackupRepository,
    onOpenSettings: () -> Unit,
    onUpdateGoal: (goalId: String, goalText: String, title: String, nativeLanguage: NativeLanguage) -> Unit,
    onResetAllProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var goalText by remember(goal.id) { mutableStateOf(goal.goalText) }
    var title by remember(goal.id) { mutableStateOf(goal.title ?: "") }
    var nativeLanguage by remember(goal.id) { mutableStateOf(goal.nativeLanguage) }
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isSubmitting = state.isSubmitting
    val errorMessage = state.errorMessage

    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { onShowResetAllConfirmChange(false) },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(Res.string.main_profile_reset_all_dialog_title)) },
            text = { Text(stringResource(Res.string.main_profile_reset_all_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onShowResetAllConfirmChange(false)
                    onResetAllProgress()
                }) { Text(stringResource(Res.string.main_profile_reset_all_confirm)) }
            },
            dismissButton = { TextButton(onClick = { onShowResetAllConfirmChange(false) }) { Text(stringResource(Res.string.main_profile_cancel)) } },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                                TextButton(onClick = { onEditingGoalChange(false) }, enabled = !isSubmitting) { Text(stringResource(Res.string.main_profile_cancel)) }
                                Button(
                                    onClick = {
                                        onUpdateGoal(goal.id, goalText, title, nativeLanguage)
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
                                IconButton(onClick = { onEditingGoalChange(true) }) {
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
                DangerZoneSection(onResetAllProgress = { onShowResetAllConfirmChange(true) })

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

private val fakeProfileGoal = Goal(id = "g1", goalText = "Learn German for my trip to Berlin", title = "Berlin Trip", nativeLanguage = NativeLanguage.ENGLISH, isActive = true, createdAt = "2026-01-01")

private val fakeProfileGoalProgress = GoalProgress(
    overallPct = 0.42f, currentLessonId = "l1", streakDays = 5, todayCompleted = false,
    vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
)

private object FakeBackupRepository : BackupRepository {
    override fun export(): String = ""
    override fun import(json: String): ApiResult<Unit> = ApiResult.Success(Unit)
}

@Preview
@Composable
private fun ProfileViewingPreview() {
    EvolaTheme {
        ProfileContent(
            goal = fakeProfileGoal, state = ProfileState(unlockedBadgeIds = setOf("streak_7"), progress = fakeProfileGoalProgress),
            isEditingGoal = false, onEditingGoalChange = {}, showResetAllConfirm = false, onShowResetAllConfirmChange = {},
            snackbarHostState = remember { SnackbarHostState() }, coroutineScope = rememberCoroutineScope(),
            backupRepository = FakeBackupRepository, onOpenSettings = {}, onUpdateGoal = { _, _, _, _ -> }, onResetAllProgress = {},
        )
    }
}

@Preview
@Composable
private fun ProfileEditingPreview() {
    EvolaTheme {
        ProfileContent(
            goal = fakeProfileGoal, state = ProfileState(unlockedBadgeIds = emptySet(), progress = fakeProfileGoalProgress),
            isEditingGoal = true, onEditingGoalChange = {}, showResetAllConfirm = false, onShowResetAllConfirmChange = {},
            snackbarHostState = remember { SnackbarHostState() }, coroutineScope = rememberCoroutineScope(),
            backupRepository = FakeBackupRepository, onOpenSettings = {}, onUpdateGoal = { _, _, _, _ -> }, onResetAllProgress = {},
        )
    }
}
