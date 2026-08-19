package evola.composeapp.onboarding

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
import androidx.compose.ui.unit.dp
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.components.SelectableChip
import evola.shared.local.LocalSettingsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val DAILY_GOAL_OPTIONS = listOf(5, 8, 12, 20)

/** Reword's onboarding daily-goal step - no pre-loaded category model exists here to pick from (see
 * this app's own "explicitly not done" note in docs/ROADMAP.md), but the daily new-word target is a
 * real, already-existing setting ([evola.shared.local.LocalSettingsRepository.setDailyNewWordGoal])
 * that's otherwise only reachable from Settings post-onboarding - surfacing it as its own onboarding
 * step, right after the goal itself, matches Reword's flow without inventing new content structure. */
@Composable
fun DailyGoalPickerScreen(onContinue: () -> Unit) {
    val settingsRepository = koinInject<LocalSettingsRepository>()
    val coroutineScope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(8) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("How many new words a day?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "You can change this any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAILY_GOAL_OPTIONS.forEach { option ->
                    SelectableChip(
                        label = "$option",
                        selected = selected == option,
                        onClick = { selected = option },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        settingsRepository.setDailyNewWordGoal(selected)
                        onContinue()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
