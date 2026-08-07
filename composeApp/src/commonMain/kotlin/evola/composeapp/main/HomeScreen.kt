@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.StatusTag
import evola.composeapp.theme.components.StatusTagStyle
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.Lesson
import evola.shared.goals.VocabularyBreakdown
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text("Your goal", style = MaterialTheme.typography.labelLarge, color = EvolaColors.Accent)
                Spacer(Modifier.height(4.dp))
                Text(goal.goalText, style = MaterialTheme.typography.headlineMedium)
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
                            DashboardBody(
                                progress = current.progress,
                                currentLesson = current.currentLesson,
                                onContinueLesson = onContinueLesson,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
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
private fun DashboardBody(
    progress: GoalProgress,
    currentLesson: Lesson?,
    onContinueLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Goal readiness", style = MaterialTheme.typography.labelLarge, color = EvolaColors.Text2)
        Spacer(Modifier.height(EvolaSpacing.lg))
        GoalReadinessRing(percent = (progress.overallPct * 100).roundToInt(), vocabulary = progress.vocabulary)
        Spacer(Modifier.height(EvolaSpacing.xl))
        VocabularyBreakdownRow(progress.vocabulary)
        Spacer(Modifier.height(EvolaSpacing.lg))
        StreakCard(progress)

        // Push the primary action to the bottom of the dashboard, as in the design.
        Spacer(Modifier.weight(1f))

        if (currentLesson != null) {
            OutlinedButton(
                onClick = { onContinueLesson(currentLesson) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, EvolaColors.Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EvolaColors.Accent),
            ) {
                Text(
                    "Continue Lesson ${currentLesson.number}: ${currentLesson.title}",
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(EvolaSpacing.sm))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EvolaColors.Accent)
                Text("You've completed every lesson!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Colors shared between the readiness ring's segments and the breakdown tiles' dots below it, so
 * the two visuals read as one system rather than two independent widgets. Deliberately three
 * distinct hues (teal / purple / muted grey) rather than shades of the same accent, since two
 * near-identical purples were hard to tell apart at a glance on the ring. */
private val MasteredColor = EvolaColors.Teal
private val LearningColor = EvolaColors.Accent
private val NotStartedColor = EvolaColors.Text3

/** Hero readiness dial: instead of a single flat accent arc, the ring is *composed* of the goal's
 * actual word-status mix - a mastered segment, then a learning segment, with whatever's left as
 * unfilled track - so the ring itself shows what the percentage is made of, not just the number.
 * Center shows the overall % plus the raw "mastered/total words" count. */
@Composable
private fun GoalReadinessRing(percent: Int, vocabulary: VocabularyBreakdown, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    Box(modifier = modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            fun arc(color: Color, startAngle: Float, sweepAngle: Float) {
                if (sweepAngle <= 0f) return
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            arc(NotStartedColor, -90f, 360f)
            if (total > 0) {
                val masteredSweep = vocabulary.mastered / total.toFloat() * 360f
                val learningSweep = vocabulary.inProgress / total.toFloat() * 360f
                arc(MasteredColor, -90f, masteredSweep)
                arc(LearningColor, -90f + masteredSweep, learningSweep)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$clamped%", style = MaterialTheme.typography.displayMedium, color = EvolaColors.Accent)
            Text("ready", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
            if (total > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${vocabulary.mastered}/$total words",
                    style = MaterialTheme.typography.labelMedium,
                    color = EvolaColors.Text3,
                )
            }
        }
    }
}

/** Word-count breakdown across the goal's whole vocabulary, bucketed from the 5-status SRS ladder
 * (unseen -> Not started, mastered -> Mastered, everything between -> Learning). Each tile's dot
 * matches its segment's color in [GoalReadinessRing] above, tying the two visuals together. */
@Composable
private fun VocabularyBreakdownRow(vocabulary: VocabularyBreakdown) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        BreakdownStat("${vocabulary.notStarted}", "Not started", NotStartedColor, Modifier.weight(1f))
        BreakdownStat("${vocabulary.inProgress}", "Learning", LearningColor, Modifier.weight(1f))
        BreakdownStat("${vocabulary.mastered}", "Mastered", MasteredColor, Modifier.weight(1f))
    }
}

@Composable
private fun BreakdownStat(value: String, label: String, dotColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small) {
        Column(
            modifier = Modifier.padding(vertical = EvolaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
            }
        }
    }
}

@Composable
private fun StreakCard(progress: GoalProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
        ) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = EvolaColors.Accent)
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
}
