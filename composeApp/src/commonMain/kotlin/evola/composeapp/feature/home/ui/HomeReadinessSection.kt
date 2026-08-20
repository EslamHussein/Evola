package evola.composeapp.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_activity_title
import evola.composeapp.generated.resources.main_home_exam_readiness
import evola.composeapp.generated.resources.main_home_no_activity
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.onboarding.domain.DayActivity
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Colors shared across the mini ring and the activity chart below it, so every "mastered/learning/
 * not started" signal on the dashboard reads as one system. */
private val MasteredColor: Color @Composable get() = EvolaColors.Accent
private val LearningColor: Color @Composable get() = EvolaColors.Ink2
private val NotStartedColor: Color @Composable get() = EvolaColors.Text3

/** Evola-original addition, no Reword equivalent - kept (per this dashboard's own earlier design
 * note: "a stronger design than Reword's own streak-only view") but no longer paired with a
 * duplicate streak tile now that [StatsSection] owns all streak content on its own, matching
 * Reword's structure. */
@Composable
internal fun TopTilesRow(percent: Int, vocabulary: VocabularyBreakdown) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessRing(percent = percent, vocabulary = vocabulary)
            Spacer(Modifier.width(EvolaSpacing.md))
            Text(stringResource(Res.string.main_home_exam_readiness), style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text2)
        }
    }
}

/** Compact readiness dial (80dp): a 3-segment ring - mastered, then learning, remainder unfilled -
 * so the ring itself shows what the % is made of, with the number centered inside it. */
@Composable
private fun ReadinessRing(percent: Int, vocabulary: VocabularyBreakdown, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    val masteredColor = MasteredColor
    val learningColor = LearningColor
    val notStartedColor = NotStartedColor
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
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
            arc(notStartedColor, -90f, 360f)
            if (total > 0) {
                val masteredSweep = vocabulary.mastered / total.toFloat() * 360f
                val learningSweep = vocabulary.inProgress / total.toFloat() * 360f
                arc(masteredColor, -90f, masteredSweep)
                arc(learningColor, -90f + masteredSweep, learningSweep)
            }
        }
        Text("$clamped%", style = MaterialTheme.typography.titleLarge)
    }
}

/** Evola-original addition, no Reword equivalent (Reword's own Stats card has no stacked bar chart)
 * - the "Learned today X/Y" readout moved into [SessionModesSection]'s "Learn new words" row instead
 * of appearing twice. */
@Composable
internal fun ActivityChartCard(progress: GoalProgress) {
    if (progress.weeklyActivity.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
            Text(stringResource(Res.string.main_home_activity_title), style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text2)
            Spacer(Modifier.height(EvolaSpacing.md))
            ActivityChart(progress.weeklyActivity)
        }
    }
}

/** Stacked new-words/reviewed bar chart, one bar per [days] entry - the mastered/learning color
 * pairing from [MasteredColor]/[LearningColor] reused here (new words = mastered-color's accent,
 * reviews = the softer ink) so the chart reads as part of the same visual system as the ring above
 * it instead of introducing a third color pairing. */
@Composable
private fun ActivityChart(days: List<DayActivity>) {
    // A week with literally zero activity used to render as thin flat hairlines with no other
    // signal on the card - easy to misread as broken rather than "nothing yet". A caption instead
    // says so directly, matching how the rest of this dashboard never shows an ambiguous empty chart.
    if (days.all { it.newWordsLearned == 0 && it.wordsReviewed == 0 }) {
        Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.main_home_no_activity), style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        }
        return
    }
    val maxCount = days.maxOf { it.newWordsLearned + it.wordsReviewed }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val total = day.newWordsLearned + day.wordsReviewed
            val barFraction = (total / maxCount.toFloat()).coerceIn(0f, 1f)
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                if (total == 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(EvolaColors.Border))
                } else {
                    // A single bar, its own height proportional to the day's total - internally
                    // split top(reviewed)/bottom(new) by weight, so the two segments' heights stay
                    // proportional to each other regardless of the outer bar's overall height.
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(barFraction)) {
                        if (day.wordsReviewed > 0) {
                            Box(modifier = Modifier.fillMaxWidth().weight(day.wordsReviewed.toFloat()).background(EvolaColors.Ink2))
                        }
                        if (day.newWordsLearned > 0) {
                            Box(modifier = Modifier.fillMaxWidth().weight(day.newWordsLearned.toFloat()).background(EvolaColors.Accent))
                        }
                    }
                }
            }
        }
    }
}

private val fakeReadinessVocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3)

private val fakeWeeklyActivity = listOf(
    DayActivity(date = "2026-08-14", hadActivity = true, newWordsLearned = 3, wordsReviewed = 5),
    DayActivity(date = "2026-08-15", hadActivity = true, newWordsLearned = 2, wordsReviewed = 4),
    DayActivity(date = "2026-08-16", hadActivity = false, newWordsLearned = 0, wordsReviewed = 0),
    DayActivity(date = "2026-08-17", hadActivity = true, newWordsLearned = 4, wordsReviewed = 2),
    DayActivity(date = "2026-08-18", hadActivity = true, newWordsLearned = 1, wordsReviewed = 6),
    DayActivity(date = "2026-08-19", hadActivity = true, newWordsLearned = 5, wordsReviewed = 3),
    DayActivity(date = "2026-08-20", hadActivity = true, newWordsLearned = 2, wordsReviewed = 1),
)

@Preview
@Composable
private fun TopTilesRowPreview() {
    EvolaTheme {
        TopTilesRow(percent = 62, vocabulary = fakeReadinessVocabulary)
    }
}

@Preview
@Composable
private fun ActivityChartCardPreview() {
    EvolaTheme {
        ActivityChartCard(
            progress = GoalProgress(
                overallPct = 0.42f, currentLessonId = "l1", streakDays = 5, todayCompleted = false,
                vocabulary = fakeReadinessVocabulary, weeklyActivity = fakeWeeklyActivity,
            ),
        )
    }
}
