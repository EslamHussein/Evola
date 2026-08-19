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
import evola.composeapp.speech.rememberSpeechService
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import evola.composeapp.theme.components.SelectableChip
import evola.shared.local.AppSettings
import evola.shared.local.AppTheme
import kotlin.math.roundToInt
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_settings_auto_pronounce_subtitle
import evola.composeapp.generated.resources.main_settings_auto_pronounce_title
import evola.composeapp.generated.resources.main_settings_back_cd
import evola.composeapp.generated.resources.main_settings_daily_word_goal_subtitle
import evola.composeapp.generated.resources.main_settings_daily_word_goal_title
import evola.composeapp.generated.resources.main_settings_decrease_cd
import evola.composeapp.generated.resources.main_settings_frequency_limit_subtitle
import evola.composeapp.generated.resources.main_settings_frequency_limit_title
import evola.composeapp.generated.resources.main_settings_increase_cd
import evola.composeapp.generated.resources.main_settings_invert_swipe_subtitle
import evola.composeapp.generated.resources.main_settings_invert_swipe_title
import evola.composeapp.generated.resources.main_settings_mc_exercise_subtitle
import evola.composeapp.generated.resources.main_settings_mc_exercise_title
import evola.composeapp.generated.resources.main_settings_reduce_motion_subtitle
import evola.composeapp.generated.resources.main_settings_reduce_motion_title
import evola.composeapp.generated.resources.main_settings_reminder_time_title
import evola.composeapp.generated.resources.main_settings_reminder_time_value
import evola.composeapp.generated.resources.main_settings_review_reminders_subtitle
import evola.composeapp.generated.resources.main_settings_review_reminders_title
import evola.composeapp.generated.resources.main_settings_section_appearance
import evola.composeapp.generated.resources.main_settings_section_general
import evola.composeapp.generated.resources.main_settings_section_notifications
import evola.composeapp.generated.resources.main_settings_section_pronunciation
import evola.composeapp.generated.resources.main_settings_show_transcription_subtitle
import evola.composeapp.generated.resources.main_settings_show_transcription_title
import evola.composeapp.generated.resources.main_settings_silent_end_subtitle
import evola.composeapp.generated.resources.main_settings_silent_end_title
import evola.composeapp.generated.resources.main_settings_silent_start_subtitle
import evola.composeapp.generated.resources.main_settings_silent_start_title
import evola.composeapp.generated.resources.main_settings_speech_rate_title
import evola.composeapp.generated.resources.main_settings_speech_rate_value
import evola.composeapp.generated.resources.main_settings_theme_label
import evola.composeapp.generated.resources.main_settings_title
import evola.composeapp.generated.resources.main_settings_tts_enabled_subtitle
import evola.composeapp.generated.resources.main_settings_tts_enabled_title
import evola.composeapp.generated.resources.main_settings_typed_exercise_subtitle
import evola.composeapp.generated.resources.main_settings_typed_exercise_title
import evola.composeapp.generated.resources.main_settings_voice_default
import evola.composeapp.generated.resources.main_settings_voice_picker_subtitle
import evola.composeapp.generated.resources.main_settings_voice_picker_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    val state by viewModel.collectAsState()
    BackHandler(onBack = onBack)

    SettingsContent(
        settings = state.settings,
        speechService = speechService,
        onBack = onBack,
        onSetAppTheme = viewModel::setAppTheme,
        onSetReducedMotion = viewModel::setReducedMotion,
        onSetDailyNewWordGoal = viewModel::setDailyNewWordGoal,
        onSetKeyboardExerciseEnabled = viewModel::setKeyboardExerciseEnabled,
        onSetMultipleChoiceExerciseEnabled = viewModel::setMultipleChoiceExerciseEnabled,
        onSetInvertSwipe = viewModel::setInvertSwipe,
        onSetTtsEnabled = viewModel::setTtsEnabled,
        onSetAutoPronounce = viewModel::setAutoPronounce,
        onSetTtsRate = viewModel::setTtsRate,
        onSetTtsVoiceName = viewModel::setTtsVoiceName,
        onSetShowTranscription = viewModel::setShowTranscription,
        onSetNotificationsEnabled = { enabled ->
            viewModel.setNotificationsEnabled(enabled)
            onNotificationsToggled(enabled)
        },
        onSetReminderHour = viewModel::setReminderHour,
        onSetSilentHoursStart = viewModel::setSilentHoursStart,
        onSetSilentHoursEnd = viewModel::setSilentHoursEnd,
        onSetNotificationFrequencyLimitHours = viewModel::setNotificationFrequencyLimitHours,
    )
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    speechService: SpeechService,
    onBack: () -> Unit,
    onSetAppTheme: (AppTheme) -> Unit,
    onSetReducedMotion: (Boolean) -> Unit,
    onSetDailyNewWordGoal: (Int) -> Unit,
    onSetKeyboardExerciseEnabled: (Boolean) -> Unit,
    onSetMultipleChoiceExerciseEnabled: (Boolean) -> Unit,
    onSetInvertSwipe: (Boolean) -> Unit,
    onSetTtsEnabled: (Boolean) -> Unit,
    onSetAutoPronounce: (Boolean) -> Unit,
    onSetTtsRate: (Float) -> Unit,
    onSetTtsVoiceName: (String?) -> Unit,
    onSetShowTranscription: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetReminderHour: (Int) -> Unit,
    onSetSilentHoursStart: (Int) -> Unit,
    onSetSilentHoursEnd: (Int) -> Unit,
    onSetNotificationFrequencyLimitHours: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.main_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.main_settings_back_cd))
                    }
                },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EvolaSpacing.xl),
            ) {
                SectionLabel(stringResource(Res.string.main_settings_section_appearance))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
                        Text(stringResource(Res.string.main_settings_theme_label), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(EvolaSpacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                            AppTheme.entries.forEach { theme ->
                                SelectableChip(
                                    label = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = settings.appTheme == theme,
                                    onClick = { onSetAppTheme(theme) },
                                )
                            }
                        }
                    }
                    DividerRow()
                    ToggleRow(
                        title = stringResource(Res.string.main_settings_reduce_motion_title),
                        subtitle = stringResource(Res.string.main_settings_reduce_motion_subtitle),
                        checked = settings.reducedMotion,
                        onCheckedChange = onSetReducedMotion,
                    )
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel(stringResource(Res.string.main_settings_section_general))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        StepperRow(
                            title = stringResource(Res.string.main_settings_daily_word_goal_title),
                            subtitle = stringResource(Res.string.main_settings_daily_word_goal_subtitle),
                            value = settings.dailyNewWordGoal,
                            range = 3..40,
                            step = 1,
                            onChange = onSetDailyNewWordGoal,
                        )
                        DividerRow()
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_typed_exercise_title),
                            subtitle = stringResource(Res.string.main_settings_typed_exercise_subtitle),
                            checked = settings.keyboardExerciseEnabled,
                            onCheckedChange = onSetKeyboardExerciseEnabled,
                        )
                        DividerRow()
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_mc_exercise_title),
                            subtitle = stringResource(Res.string.main_settings_mc_exercise_subtitle),
                            checked = settings.multipleChoiceExerciseEnabled,
                            onCheckedChange = onSetMultipleChoiceExerciseEnabled,
                        )
                        DividerRow()
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_invert_swipe_title),
                            subtitle = stringResource(Res.string.main_settings_invert_swipe_subtitle),
                            checked = settings.invertSwipe,
                            onCheckedChange = onSetInvertSwipe,
                        )
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel(stringResource(Res.string.main_settings_section_pronunciation))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_tts_enabled_title),
                            subtitle = stringResource(Res.string.main_settings_tts_enabled_subtitle),
                            checked = settings.ttsEnabled,
                            onCheckedChange = onSetTtsEnabled,
                        )
                        if (settings.ttsEnabled) {
                            DividerRow()
                            ToggleRow(
                                title = stringResource(Res.string.main_settings_auto_pronounce_title),
                                subtitle = stringResource(Res.string.main_settings_auto_pronounce_subtitle),
                                checked = settings.autoPronounce,
                                onCheckedChange = onSetAutoPronounce,
                            )
                            DividerRow()
                            SpeechRateRow(rate = settings.ttsRate, onChange = onSetTtsRate)
                            DividerRow()
                            VoicePickerRow(
                                speechService = speechService,
                                selected = settings.ttsVoiceName,
                                onSelect = onSetTtsVoiceName,
                            )
                        }
                        DividerRow()
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_show_transcription_title),
                            subtitle = stringResource(Res.string.main_settings_show_transcription_subtitle),
                            checked = settings.showTranscription,
                            onCheckedChange = onSetShowTranscription,
                        )
                    }
                }

                Spacer(Modifier.height(EvolaSpacing.xxl))
                SectionLabel(stringResource(Res.string.main_settings_section_notifications))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(
                            title = stringResource(Res.string.main_settings_review_reminders_title),
                            subtitle = stringResource(Res.string.main_settings_review_reminders_subtitle),
                            checked = settings.notificationsEnabled,
                            onCheckedChange = onSetNotificationsEnabled,
                        )
                        if (settings.notificationsEnabled) {
                            DividerRow()
                            StepperRow(
                                title = stringResource(Res.string.main_settings_reminder_time_title),
                                subtitle = stringResource(Res.string.main_settings_reminder_time_value, settings.reminderHour),
                                value = settings.reminderHour,
                                range = 0..23,
                                step = 1,
                                onChange = onSetReminderHour,
                            )
                            DividerRow()
                            StepperRow(
                                title = stringResource(Res.string.main_settings_silent_start_title),
                                subtitle = stringResource(Res.string.main_settings_silent_start_subtitle),
                                value = settings.silentHoursStart,
                                range = 0..23,
                                step = 1,
                                onChange = onSetSilentHoursStart,
                            )
                            DividerRow()
                            StepperRow(
                                title = stringResource(Res.string.main_settings_silent_end_title),
                                subtitle = stringResource(Res.string.main_settings_silent_end_subtitle),
                                value = settings.silentHoursEnd,
                                range = 0..23,
                                step = 1,
                                onChange = onSetSilentHoursEnd,
                            )
                            DividerRow()
                            StepperRow(
                                title = stringResource(Res.string.main_settings_frequency_limit_title),
                                subtitle = stringResource(Res.string.main_settings_frequency_limit_subtitle),
                                value = settings.notificationFrequencyLimitHours,
                                range = 1..24,
                                step = 1,
                                onChange = onSetNotificationFrequencyLimitHours,
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
            Icon(Icons.Filled.Remove, contentDescription = stringResource(Res.string.main_settings_decrease_cd))
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.main_settings_increase_cd))
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
            Text(stringResource(Res.string.main_settings_voice_picker_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(Res.string.main_settings_voice_picker_subtitle), style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
        }
        val defaultVoiceLabel = stringResource(Res.string.main_settings_voice_default)
        Box {
            Surface(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = EvolaColors.SurfaceAlt,
            ) {
                Text(
                    selected ?: defaultVoiceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(defaultVoiceLabel) }, onClick = { onSelect(null); expanded = false })
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
            Text(stringResource(Res.string.main_settings_speech_rate_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.main_settings_speech_rate_value, (rate * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        }
        Slider(value = rate, onValueChange = onChange, valueRange = 0.5f..2f)
    }
}

private val fakeSettingsContentActions = object {
    val onBack: () -> Unit = {}
    val onSetAppTheme: (AppTheme) -> Unit = {}
    val onSetReducedMotion: (Boolean) -> Unit = {}
    val onSetDailyNewWordGoal: (Int) -> Unit = {}
    val onSetKeyboardExerciseEnabled: (Boolean) -> Unit = {}
    val onSetMultipleChoiceExerciseEnabled: (Boolean) -> Unit = {}
    val onSetInvertSwipe: (Boolean) -> Unit = {}
    val onSetTtsEnabled: (Boolean) -> Unit = {}
    val onSetAutoPronounce: (Boolean) -> Unit = {}
    val onSetTtsRate: (Float) -> Unit = {}
    val onSetTtsVoiceName: (String?) -> Unit = {}
    val onSetShowTranscription: (Boolean) -> Unit = {}
    val onSetNotificationsEnabled: (Boolean) -> Unit = {}
    val onSetReminderHour: (Int) -> Unit = {}
    val onSetSilentHoursStart: (Int) -> Unit = {}
    val onSetSilentHoursEnd: (Int) -> Unit = {}
    val onSetNotificationFrequencyLimitHours: (Int) -> Unit = {}
}

@Preview
@Composable
private fun SettingsContentDefaultPreview() {
    EvolaTheme {
        SettingsContent(
            settings = AppSettings(),
            speechService = rememberSpeechService(),
            onBack = fakeSettingsContentActions.onBack,
            onSetAppTheme = fakeSettingsContentActions.onSetAppTheme,
            onSetReducedMotion = fakeSettingsContentActions.onSetReducedMotion,
            onSetDailyNewWordGoal = fakeSettingsContentActions.onSetDailyNewWordGoal,
            onSetKeyboardExerciseEnabled = fakeSettingsContentActions.onSetKeyboardExerciseEnabled,
            onSetMultipleChoiceExerciseEnabled = fakeSettingsContentActions.onSetMultipleChoiceExerciseEnabled,
            onSetInvertSwipe = fakeSettingsContentActions.onSetInvertSwipe,
            onSetTtsEnabled = fakeSettingsContentActions.onSetTtsEnabled,
            onSetAutoPronounce = fakeSettingsContentActions.onSetAutoPronounce,
            onSetTtsRate = fakeSettingsContentActions.onSetTtsRate,
            onSetTtsVoiceName = fakeSettingsContentActions.onSetTtsVoiceName,
            onSetShowTranscription = fakeSettingsContentActions.onSetShowTranscription,
            onSetNotificationsEnabled = fakeSettingsContentActions.onSetNotificationsEnabled,
            onSetReminderHour = fakeSettingsContentActions.onSetReminderHour,
            onSetSilentHoursStart = fakeSettingsContentActions.onSetSilentHoursStart,
            onSetSilentHoursEnd = fakeSettingsContentActions.onSetSilentHoursEnd,
            onSetNotificationFrequencyLimitHours = fakeSettingsContentActions.onSetNotificationFrequencyLimitHours,
        )
    }
}

@Preview
@Composable
private fun SettingsContentPronunciationExpandedPreview() {
    EvolaTheme {
        SettingsContent(
            settings = AppSettings(ttsEnabled = true, autoPronounce = true, ttsRate = 1f),
            speechService = rememberSpeechService(),
            onBack = fakeSettingsContentActions.onBack,
            onSetAppTheme = fakeSettingsContentActions.onSetAppTheme,
            onSetReducedMotion = fakeSettingsContentActions.onSetReducedMotion,
            onSetDailyNewWordGoal = fakeSettingsContentActions.onSetDailyNewWordGoal,
            onSetKeyboardExerciseEnabled = fakeSettingsContentActions.onSetKeyboardExerciseEnabled,
            onSetMultipleChoiceExerciseEnabled = fakeSettingsContentActions.onSetMultipleChoiceExerciseEnabled,
            onSetInvertSwipe = fakeSettingsContentActions.onSetInvertSwipe,
            onSetTtsEnabled = fakeSettingsContentActions.onSetTtsEnabled,
            onSetAutoPronounce = fakeSettingsContentActions.onSetAutoPronounce,
            onSetTtsRate = fakeSettingsContentActions.onSetTtsRate,
            onSetTtsVoiceName = fakeSettingsContentActions.onSetTtsVoiceName,
            onSetShowTranscription = fakeSettingsContentActions.onSetShowTranscription,
            onSetNotificationsEnabled = fakeSettingsContentActions.onSetNotificationsEnabled,
            onSetReminderHour = fakeSettingsContentActions.onSetReminderHour,
            onSetSilentHoursStart = fakeSettingsContentActions.onSetSilentHoursStart,
            onSetSilentHoursEnd = fakeSettingsContentActions.onSetSilentHoursEnd,
            onSetNotificationFrequencyLimitHours = fakeSettingsContentActions.onSetNotificationFrequencyLimitHours,
        )
    }
}

@Preview
@Composable
private fun SettingsContentNotificationsExpandedPreview() {
    EvolaTheme {
        SettingsContent(
            settings = AppSettings(notificationsEnabled = true, reminderHour = 19, silentHoursStart = 22, silentHoursEnd = 7),
            speechService = rememberSpeechService(),
            onBack = fakeSettingsContentActions.onBack,
            onSetAppTheme = fakeSettingsContentActions.onSetAppTheme,
            onSetReducedMotion = fakeSettingsContentActions.onSetReducedMotion,
            onSetDailyNewWordGoal = fakeSettingsContentActions.onSetDailyNewWordGoal,
            onSetKeyboardExerciseEnabled = fakeSettingsContentActions.onSetKeyboardExerciseEnabled,
            onSetMultipleChoiceExerciseEnabled = fakeSettingsContentActions.onSetMultipleChoiceExerciseEnabled,
            onSetInvertSwipe = fakeSettingsContentActions.onSetInvertSwipe,
            onSetTtsEnabled = fakeSettingsContentActions.onSetTtsEnabled,
            onSetAutoPronounce = fakeSettingsContentActions.onSetAutoPronounce,
            onSetTtsRate = fakeSettingsContentActions.onSetTtsRate,
            onSetTtsVoiceName = fakeSettingsContentActions.onSetTtsVoiceName,
            onSetShowTranscription = fakeSettingsContentActions.onSetShowTranscription,
            onSetNotificationsEnabled = fakeSettingsContentActions.onSetNotificationsEnabled,
            onSetReminderHour = fakeSettingsContentActions.onSetReminderHour,
            onSetSilentHoursStart = fakeSettingsContentActions.onSetSilentHoursStart,
            onSetSilentHoursEnd = fakeSettingsContentActions.onSetSilentHoursEnd,
            onSetNotificationFrequencyLimitHours = fakeSettingsContentActions.onSetNotificationFrequencyLimitHours,
        )
    }
}
