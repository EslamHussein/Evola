@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import evola.composeapp.BackHandler
import evola.composeapp.speech.SpeechService
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.SelectableChip
import evola.shared.local.AppTheme
import kotlin.math.roundToInt
import pro.respawn.flowmvi.compose.dsl.subscribe

/**
 * Every user-tunable knob Evola has, grouped the way Reword's own Settings screen does (General /
 * Notifications / Pronunciation) but rendered in this app's existing Card+row language rather than
 * Reword's flat list. [onNotificationsToggled] is a separate callback (not just a setter call) since
 * turning notifications on needs to trigger the platform permission prompt one layer up, in
 * [MainScreen] - this screen only reflects the resulting on/off state, it doesn't own the permission
 * flow itself.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    speechService: SpeechService,
    onBack: () -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
) {
    val state by viewModel.subscribe()
    val settings = state.settings
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EvolaSpacing.xl),
            ) {
                SectionLabel("Appearance")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(EvolaSpacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                            AppTheme.entries.forEach { theme ->
                                SelectableChip(
                                    label = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = settings.appTheme == theme,
                                    onClick = { viewModel.intent(SettingsIntent.SetAppTheme(theme)) },
                                )
                            }
                        }
                    }
                    DividerRow()
                    ToggleRow(
                        title = "Reduce motion",
                        subtitle = "Fewer/instant transitions during a session",
                        checked = settings.reducedMotion,
                        onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetReducedMotion(value)) },
                    )
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel("General")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        StepperRow(
                            title = "Daily new-word goal",
                            subtitle = "New words introduced per session",
                            value = settings.dailyNewWordGoal,
                            range = 3..40,
                            step = 1,
                            onChange = { value -> viewModel.intent(SettingsIntent.SetDailyNewWordGoal(value)) },
                        )
                        DividerRow()
                        ToggleRow(
                            title = "Typed-answer exercise",
                            subtitle = "Type the word from its meaning",
                            checked = settings.keyboardExerciseEnabled,
                            onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetKeyboardExerciseEnabled(value)) },
                        )
                        DividerRow()
                        ToggleRow(
                            title = "Multiple-choice exercise",
                            subtitle = "Pick the word from four options",
                            checked = settings.multipleChoiceExerciseEnabled,
                            onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetMultipleChoiceExerciseEnabled(value)) },
                        )
                        DividerRow()
                        ToggleRow(
                            title = "Invert swipe direction",
                            subtitle = "Swap which side means \"I know it\"",
                            checked = settings.invertSwipe,
                            onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetInvertSwipe(value)) },
                        )
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel("Pronunciation")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            title = "Speak words aloud",
                            subtitle = "Tap the audio icon during a session",
                            checked = settings.ttsEnabled,
                            onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetTtsEnabled(value)) },
                        )
                        if (settings.ttsEnabled) {
                            DividerRow()
                            ToggleRow(
                                title = "Automatically pronounce",
                                subtitle = "Speak the word as soon as a card appears",
                                checked = settings.autoPronounce,
                                onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetAutoPronounce(value)) },
                            )
                            DividerRow()
                            SpeechRateRow(rate = settings.ttsRate, onChange = { value -> viewModel.intent(SettingsIntent.SetTtsRate(value)) })
                            DividerRow()
                            VoicePickerRow(
                                speechService = speechService,
                                selected = settings.ttsVoiceName,
                                onSelect = { value -> viewModel.intent(SettingsIntent.SetTtsVoiceName(value)) },
                            )
                        }
                        DividerRow()
                        ToggleRow(
                            title = "Show transcription",
                            subtitle = "IPA pronunciation guide, e.g. /haʊs/",
                            checked = settings.showTranscription,
                            onCheckedChange = { value -> viewModel.intent(SettingsIntent.SetShowTranscription(value)) },
                        )
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel("Notifications")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            title = "Review reminders",
                            subtitle = "A daily nudge when words are due",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.intent(SettingsIntent.SetNotificationsEnabled(enabled))
                                onNotificationsToggled(enabled)
                            },
                        )
                        if (settings.notificationsEnabled) {
                            DividerRow()
                            StepperRow(
                                title = "Reminder time",
                                subtitle = "${settings.reminderHour}:00",
                                value = settings.reminderHour,
                                range = 0..23,
                                step = 1,
                                onChange = { value -> viewModel.intent(SettingsIntent.SetReminderHour(value)) },
                            )
                            DividerRow()
                            StepperRow(
                                title = "Silent hours start",
                                subtitle = "No reminders from this hour…",
                                value = settings.silentHoursStart,
                                range = 0..23,
                                step = 1,
                                onChange = { value -> viewModel.intent(SettingsIntent.SetSilentHoursStart(value)) },
                            )
                            DividerRow()
                            StepperRow(
                                title = "Silent hours end",
                                subtitle = "…until this hour",
                                value = settings.silentHoursEnd,
                                range = 0..23,
                                step = 1,
                                onChange = { value -> viewModel.intent(SettingsIntent.SetSilentHoursEnd(value)) },
                            )
                            DividerRow()
                            StepperRow(
                                title = "Frequency limit",
                                subtitle = "Minimum hours between reminders",
                                value = settings.notificationFrequencyLimitHours,
                                range = 1..24,
                                step = 1,
                                onChange = { value -> viewModel.intent(SettingsIntent.SetNotificationFrequencyLimitHours(value)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.semantics { heading() }.padding(bottom = EvolaSpacing.sm),
    )
}

@Composable
private fun DividerRow() {
    Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(title: String, subtitle: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

/** Reword's "Robot voice" picker - lists whatever German voices the platform TTS engine currently
 * reports (see [SpeechService.availableVoiceNames]), plus a fixed "Default" option that clears
 * back to the engine's own choice. */
@Composable
private fun VoicePickerRow(speechService: SpeechService, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val voices = remember { speechService.availableVoiceNames() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Robot voice", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text("Which installed voice speaks German words", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        Box {
            Surface(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = EvolaColors.SurfaceAlt,
            ) {
                Text(
                    selected ?: "Default",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Default") }, onClick = { onSelect(null); expanded = false })
                voices.forEach { voice ->
                    DropdownMenuItem(text = { Text(voice) }, onClick = { onSelect(voice); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun SpeechRateRow(rate: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speech rate", style = MaterialTheme.typography.titleSmall)
            Text("${((rate * 100).roundToInt())}%", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        }
        Slider(value = rate, onValueChange = onChange, valueRange = 0.5f..2f)
    }
}
