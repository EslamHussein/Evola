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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.CircularProgressRing
import evola.composeapp.theme.components.StatusTag
import evola.composeapp.theme.components.StatusTagStyle
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.Lesson
import kotlin.math.roundToInt

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10). Three honest states: the encouraging
 * empty state when the goal has no lessons yet (never a broken 0% chart), a real readiness dial +
 * streak + "continue" CTA once there's something to study, and an all-complete celebration when
 * every lesson is done. */
@Composable
fun HomeScreen(
    goal: Goal,
    viewModel: HomeViewModel,
    onGoToMaterials: () -> Unit,
    onContinueLesson: (Lesson) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(goal.title ?: "Your journey") }) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text("Your goal", style = MaterialTheme.typography.labelLarge, color = EvolaColors.Teal)
                Spacer(Modifier.height(4.dp))
                Text(goal.goalText, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (val current = state) {
                    is HomeState.Loading -> CenteredBox { CircularProgressIndicator() }

                    is HomeState.Error -> CenteredBox {
                        Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(EvolaSpacing.md))
                        Button(onClick = viewModel::refresh) { Text("Retry") }
                    }

                    is HomeState.Loaded ->
                        if (!current.hasLessons) {
                            EmptyState(onGoToMaterials)
                        } else {
                            DashboardBody(current.progress, current.currentLesson, onContinueLesson)
                        }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun EmptyState(onGoToMaterials: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Text("No lessons yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                "Upload a book or study material and Evola will generate lessons for you.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(EvolaSpacing.md))
            Button(onClick = onGoToMaterials, modifier = Modifier.fillMaxWidth()) {
                Text("Upload a material")
            }
        }
    }
}

@Composable
private fun DashboardBody(progress: GoalProgress, currentLesson: Lesson?, onContinueLesson: (Lesson) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvolaSpacing.lg),
    ) {
        Text("Goal readiness", style = MaterialTheme.typography.labelLarge, color = EvolaColors.Text2)
        CircularProgressRing(percent = (progress.overallPct * 100).roundToInt(), size = 160.dp)

        StreakRow(progress)

        if (currentLesson != null) {
            Button(onClick = { onContinueLesson(currentLesson) }, modifier = Modifier.fillMaxWidth()) {
                Text("Continue Lesson ${currentLesson.number}: ${currentLesson.title}")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EvolaColors.Gold)
                Text("You've completed every lesson!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StreakRow(progress: GoalProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = EvolaColors.Gold)
        Text(
            if (progress.streakDays == 1) "1 day streak" else "${progress.streakDays} day streak",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (progress.todayCompleted) {
            StatusTag("Done today", StatusTagStyle.FILLED)
        } else {
            StatusTag("Not done yet", StatusTagStyle.NEUTRAL)
        }
    }
}
