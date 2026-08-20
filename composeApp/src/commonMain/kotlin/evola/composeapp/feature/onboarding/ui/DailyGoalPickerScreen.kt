package evola.composeapp.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.onboarding_daily_goal_continue
import evola.composeapp.generated.resources.onboarding_daily_goal_description
import evola.composeapp.generated.resources.onboarding_daily_goal_prompt
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.SelectableChip
import evola.shared.feature.profile.domain.SettingsRepository
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

private val DAILY_GOAL_OPTIONS = listOf(5, 8, 12, 20)

/** Reword's onboarding daily-goal step - no pre-loaded category model exists here to pick from (see
 * this app's own "explicitly not done" note in docs/ROADMAP.md), but the daily new-word target is a
 * real, already-existing setting ([evola.shared.feature.profile.data.LocalSettingsRepository.setDailyNewWordGoal])
 * that's otherwise only reachable from Settings post-onboarding - surfacing it as its own onboarding
 * step, right after the goal itself, matches Reword's flow without inventing new content structure. */
@Composable
fun DailyGoalPickerScreen(onContinue: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val coroutineScope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(8) }

    DailyGoalPickerContent(
        selected = selected,
        onSelectedChange = { selected = it },
        onContinue = {
            coroutineScope.launch {
                settingsRepository.setDailyNewWordGoal(selected)
                onContinue()
            }
        },
    )
}

@Composable
private fun DailyGoalPickerContent(
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(Res.string.onboarding_daily_goal_prompt), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.onboarding_daily_goal_description),
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                DAILY_GOAL_OPTIONS.forEach { option ->
                    SelectableChip(
                        label = "$option",
                        selected = selected == option,
                        onClick = { onSelectedChange(option) },
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.xxl))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.onboarding_daily_goal_continue))
            }
        }
    }
}

@Preview
@Composable
private fun DailyGoalPickerContentPreview() {
    EvolaTheme {
        DailyGoalPickerContent(selected = 8, onSelectedChange = {}, onContinue = {})
    }
}
