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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
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
import evola.composeapp.KEY_ANTHROPIC_API_KEY
import evola.composeapp.backup.rememberBackupFileLoader
import evola.composeapp.backup.rememberBackupFileSaver
import evola.composeapp.rememberSecureStore
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.IconTile
import evola.composeapp.theme.components.RootTopBarTitle
import evola.composeapp.theme.components.SelectableChip
import evola.composeapp.theme.components.StatusTag
import evola.composeapp.theme.components.StatusTagStyle
import evola.shared.core.ApiResult
import evola.shared.goals.Goal
import evola.shared.language.NativeLanguage
import kotlinx.coroutines.launch

/** Profile tab per 06_SCREENS_REFERENCE.md - account info + goal editing + sign-out.
 * Notifications/subscription/privacy are explicitly out of MVP scope (01_PRODUCT_SPEC.md), so
 * they aren't stubbed here at all rather than shown as fake placeholders.
 *
 * Redesigned as two "identity" cards (goal, AI key) that follow the same icon-tile + heading
 * language already used for lesson/material rows elsewhere in the app, rather than plain text
 * blocks - so Profile reads as part of the same design system instead of a bare settings form. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    goal: Goal,
    viewModel: ProfileViewModel,
    onGoalUpdated: (Goal) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val backupRepository = org.koin.compose.koinInject<evola.shared.local.BackupRepository>()
    val achievementsRepository = org.koin.compose.koinInject<evola.shared.achievements.AchievementsRepository>()
    val goalsRepository = org.koin.compose.koinInject<evola.shared.goals.GoalsRepository>()
    var unlockedBadgeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var latestProgress by remember { mutableStateOf<evola.shared.goals.GoalProgress?>(null) }
    LaunchedEffect(Unit) {
        unlockedBadgeIds = (achievementsRepository.unlockedBadgeIds() as? evola.shared.core.ApiResult.Success)?.data.orEmpty()
    }
    LaunchedEffect(goal.id) {
        latestProgress = (goalsRepository.getProgress(goal.id, evola.shared.todayLocalDate()) as? evola.shared.core.ApiResult.Success)?.data
    }
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
    LaunchedEffect(state.goalUpdated?.id) {
        state.goalUpdated?.let { event ->
            onGoalUpdated(event.goal)
            isEditingGoal = false
            snackbarHostState.showSnackbar("Goal updated")
        }
    }
    LaunchedEffect(state.progressReset?.id) {
        state.progressReset?.let { event ->
            snackbarHostState.showSnackbar(if (event.success) "All progress reset" else "Couldn't reset progress")
        }
    }

    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Reset all progress?") },
            text = { Text("Every word across every lesson goes back to \"New\". This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetAllConfirm = false
                    viewModel.intent(ProfileIntent.ResetAllProgress)
                }) { Text("Reset everything") }
            },
            dismissButton = { TextButton(onClick = { showResetAllConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { RootTopBarTitle("Profile") }, scrollBehavior = scrollBehavior) },
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
                    "Your goal",
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
                                label = { Text("Goal") },
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
                                label = { Text("Journey title") },
                                enabled = !isSubmitting,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(EvolaSpacing.md))
                            Text("Native language", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(EvolaSpacing.xs))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                                NativeLanguage.entries.forEach { language ->
                                    SelectableChip(
                                        label = "${language.englishName} - ${language.nativeName}",
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
                                TextButton(onClick = { isEditingGoal = false }, enabled = !isSubmitting) { Text("Cancel") }
                                Button(
                                    onClick = {
                                        viewModel.intent(ProfileIntent.UpdateGoal(goal.id, goalText, title, nativeLanguage))
                                    },
                                    enabled = !isSubmitting && goalText.trim().length >= 3,
                                ) {
                                    Text(if (isSubmitting) "Saving..." else "Save")
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.Top) {
                                IconTile(Icons.Filled.Flag, locked = false)
                                Spacer(Modifier.width(EvolaSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.title ?: "Your journey", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(goal.goalText, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                                }
                                IconButton(onClick = { isEditingGoal = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit goal", tint = EvolaColors.Text2)
                                }
                            }
                            Spacer(Modifier.height(EvolaSpacing.md))
                            LanguageBadge(goal.nativeLanguage)
                        }
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AchievementsSection(unlockedBadgeIds)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AnthropicKeySection(snackbarHostState, coroutineScope)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                AppSection(onOpenSettings, backupRepository, latestProgress, goal, snackbarHostState, coroutineScope)

                Spacer(Modifier.height(EvolaSpacing.xxl))
                DangerZoneSection(onResetAllProgress = { showResetAllConfirm = true })

                Spacer(Modifier.height(EvolaSpacing.xxl))
                CreditsSection()
            }
        }
    }
}

/** Settings entry point + local backup/restore - a JSON file export/import
 * ([evola.shared.local.BackupRepository]) rather than any cloud sync, matching this app's no-
 * account, fully-local design. */
@Composable
private fun AppSection(
    onOpenSettings: () -> Unit,
    backupRepository: evola.shared.local.BackupRepository,
    latestProgress: evola.shared.goals.GoalProgress?,
    goal: Goal,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val saveBackup = rememberBackupFileSaver(
        content = { backupRepository.export() },
        onSaved = { saved -> if (saved) coroutineScope.launch { snackbarHostState.showSnackbar("Backup saved") } },
    )
    val loadBackup = rememberBackupFileLoader { json ->
        when (backupRepository.import(json)) {
            is ApiResult.Success -> coroutineScope.launch { snackbarHostState.showSnackbar("Backup restored - restart Evola to see it") }
            is ApiResult.Failure -> coroutineScope.launch { snackbarHostState.showSnackbar("Couldn't restore that file") }
        }
    }
    val shareText = evola.composeapp.share.rememberShareText()

    Text("App", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AppRow(Icons.Filled.Settings, "Settings", "Daily goal, exercises, pronunciation, reminders", onClick = onOpenSettings)
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(
                Icons.AutoMirrored.Filled.TrendingUp,
                "Share progress",
                "Share a summary of your streak and mastered words",
                onClick = { shareText(progressShareText(goal, latestProgress)) },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(Icons.Filled.CloudUpload, "Create backup", "Save your goals, materials, and progress to a file", onClick = saveBackup)
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            AppRow(Icons.Filled.CloudDownload, "Restore backup", "Replace everything with a previously saved file", onClick = loadBackup)
        }
    }
}

/** Reword's "Share progress" summary text - built from whatever [latestProgress] is available at
 * share time; a null/never-loaded progress still produces a sensible (if less detailed) message
 * rather than blocking the share action on a fetch. */
private fun progressShareText(goal: Goal, progress: evola.shared.goals.GoalProgress?): String {
    if (progress == null) return "I'm learning German with Evola! Working toward: ${goal.goalText}"
    val streakPart = when {
        progress.streakDays > 1 -> "on a ${progress.streakDays}-day streak"
        progress.streakDays == 1 -> "on a 1-day streak"
        else -> "getting started"
    }
    return "I've mastered ${progress.vocabulary.mastered} German words on Evola, $streakPart! " +
        "Working toward: ${goal.goalText}"
}

@Composable
private fun AppRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, locked = false)
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EvolaColors.Text3)
    }
}

/** Reword's achievement badges - a fixed grid of every [evola.shared.achievements.ALL_BADGES]
 * entry, locked/unlocked styled by [unlockedBadgeIds] rather than only showing what's earned so
 * far - seeing what's still ahead is part of the point. */
@Composable
private fun AchievementsSection(unlockedBadgeIds: Set<String>) {
    Text("Achievements", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            evola.shared.achievements.ALL_BADGES.forEachIndexed { index, badge ->
                if (index > 0) {
                    Spacer(Modifier.height(EvolaSpacing.sm))
                }
                val unlocked = badge.id in unlockedBadgeIds
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (unlocked) EvolaColors.Gold else EvolaColors.Text3,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(EvolaSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            badge.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (unlocked) EvolaColors.Text else EvolaColors.Text3,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(badge.description, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                    }
                }
            }
        }
    }
}

/** Reword's Menu "Reset all progress" - a single destructive row, kept separate from [AppSection]
 * so it doesn't sit next to routine actions like Settings/backup. */
@Composable
private fun DangerZoneSection(onResetAllProgress: () -> Unit) {
    Text("Danger zone", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        AppRow(
            Icons.Filled.Restore,
            "Reset all progress",
            "Every word across every lesson goes back to \"New\"",
            onClick = onResetAllProgress,
        )
    }
}

/** Attribution for bundled third-party data, per its license terms - required regardless of
 * whether the German-noun-lookup feature built on top of it (see
 * [evola.shared.vocabulary.GermanNounLexicon]) is finished yet, since the dataset already ships
 * inside the app binary once bundled as a resource. */
@Composable
private fun CreditsSection() {
    Text("Credits", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(
        "German noun data from gambolputty/german-nouns, compiled from WiktionaryDE, licensed under CC BY-SA 4.0.",
        style = MaterialTheme.typography.bodySmall,
        color = EvolaColors.Text3,
    )
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
                "${language.englishName} • ${language.nativeName}",
                style = MaterialTheme.typography.labelSmall,
                color = EvolaColors.Text2,
            )
        }
    }
}

/** On-device Anthropic API key entry — Evola calls Claude directly (serverless), so the key lives
 * in the device's encrypted store, never on a server. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AnthropicKeySection(
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    val secureStore = rememberSecureStore()
    var savedKey by remember { mutableStateOf(secureStore.get(KEY_ANTHROPIC_API_KEY)) }
    var draft by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val looksMalformed = draft.isNotBlank() && !draft.trim().startsWith("sk-ant-")
    val isConnected = !savedKey.isNullOrBlank()

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Remove API key?") },
            text = { Text("Evola won't be able to generate new lessons or vocabulary until you add a key again.") },
            confirmButton = {
                TextButton(onClick = {
                    secureStore.remove(KEY_ANTHROPIC_API_KEY)
                    savedKey = null
                    draft = ""
                    showClearConfirm = false
                    coroutineScope.launch { snackbarHostState.showSnackbar("API key removed") }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } },
        )
    }

    Text(
        "AI (Claude) key",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Key, locked = !isConnected)
                Spacer(Modifier.width(EvolaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anthropic API key", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    StatusTag(
                        if (isConnected) "Connected" else "Not set",
                        if (isConnected) StatusTagStyle.FILLED else StatusTagStyle.NEUTRAL,
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))
            Text(
                if (!isConnected) {
                    "Evola needs your Anthropic API key to generate lessons on this device."
                } else {
                    "Enter a new key below to replace the one saved on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Anthropic API key") },
                singleLine = true,
                isError = looksMalformed,
                supportingText = if (looksMalformed) {
                    { Text("Anthropic keys usually start with \"sk-ant-\" — double-check before saving.") }
                } else {
                    null
                },
                // No PasswordVisualTransformation: on iOS, Compose Multiplatform disables the
                // paste/text-actions menu entirely on any masked field (JetBrains/compose-
                // multiplatform#4502) - since pasting a copied key is the actual workflow here,
                // masking would break the field's main use case. The key isn't shown anywhere
                // else once saved (draft clears after save; only "Connected"/"Not set" is shown).
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(EvolaSpacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm, Alignment.End)) {
                if (isConnected) {
                    TextButton(onClick = { showClearConfirm = true }) { Text("Clear") }
                }
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        secureStore.put(KEY_ANTHROPIC_API_KEY, trimmed)
                        savedKey = trimmed
                        draft = ""
                        focusManager.clearFocus()
                        coroutineScope.launch { snackbarHostState.showSnackbar("API key saved") }
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) { Text("Save key") }
            }
        }
    }

    Spacer(Modifier.height(EvolaSpacing.xxl))
    Text(
        "Evola • v1.0",
        style = MaterialTheme.typography.labelSmall,
        color = EvolaColors.Text3,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
