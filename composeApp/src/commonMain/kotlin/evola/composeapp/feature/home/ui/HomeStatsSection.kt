package evola.composeapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_best_streak
import evola.composeapp.generated.resources.main_home_current_streak
import evola.composeapp.generated.resources.main_home_day_plural
import evola.composeapp.generated.resources.main_home_day_singular
import evola.composeapp.generated.resources.main_home_share
import evola.composeapp.generated.resources.main_home_share_no_streak_message
import evola.composeapp.generated.resources.main_home_share_streak_message
import evola.composeapp.generated.resources.main_home_stats_title
import evola.composeapp.generated.resources.main_home_streak_freeze_available_plural
import evola.composeapp.generated.resources.main_home_streak_freeze_available_singular
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.onboarding.domain.DayActivity
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Reword's "Stats" section - a day-of-week strip (already built as [WeeklyStreakStrip]), two big
 * Current/Best streak tiles, and a Share row, all in one card, matching the real app's structure
 * (confirmed against a live screenshot). [evola.composeapp.core.common.rememberShareText] is the same
 * platform share sheet Profile's own "Share progress" row uses. */
@Composable
internal fun StatsSection(progress: GoalProgress) {
    val shareText = evola.composeapp.core.common.rememberShareText()
    Text(stringResource(Res.string.main_home_stats_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    val streakShareMessage = stringResource(Res.string.main_home_share_streak_message, progress.streakDays)
    val noStreakShareMessage = stringResource(Res.string.main_home_share_no_streak_message)
    val streakFreezeText = if (progress.streakFreezesAvailable == 1) {
        stringResource(Res.string.main_home_streak_freeze_available_singular, progress.streakFreezesAvailable)
    } else {
        stringResource(Res.string.main_home_streak_freeze_available_plural, progress.streakFreezesAvailable)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
            if (progress.weeklyActivity.isNotEmpty()) {
                WeeklyStreakStrip(progress.weeklyActivity)
                Spacer(Modifier.height(EvolaSpacing.lg))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                StreakTile(stringResource(Res.string.main_home_current_streak), progress.streakDays, modifier = Modifier.weight(1f))
                StreakTile(stringResource(Res.string.main_home_best_streak), progress.bestStreakDays, modifier = Modifier.weight(1f))
            }
            if (progress.streakFreezesAvailable > 0) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AcUnit, contentDescription = null, tint = EvolaColors.Text3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(EvolaSpacing.xs))
                    Text(
                        streakFreezeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = EvolaColors.Text3,
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        shareText(
                            if (progress.streakDays > 0) {
                                streakShareMessage
                            } else {
                                noStreakShareMessage
                            },
                        )
                    }
                    .padding(top = EvolaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text(stringResource(Res.string.main_home_share), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun StreakTile(label: String, days: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$days", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(if (days == 1) Res.string.main_home_day_singular else Res.string.main_home_day_plural),
                    style = MaterialTheme.typography.bodySmall,
                    color = EvolaColors.Text3,
                )
            }
        }
    }
}

/** Seven circles, oldest day first, today last - filled + a day-of-week initial when that day had
 * any completed session, outlined otherwise. Same "which days did I show up" signal as Reword's own
 * weekly strip, built from [evola.shared.feature.onboarding.data.LocalGoalsRepository]'s per-day [DayActivity] list
 * rather than a separate calendar widget. */
@Composable
private fun WeeklyStreakStrip(days: List<DayActivity>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { index, day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            val label = date?.dayOfWeek?.let(::dayInitial) ?: "?"
            // Oldest-first, today last (see this list's own construction) - Reword highlights the
            // selected day with a filled circle and a small triangle pointer underneath; today is
            // this strip's equivalent "selected" day, marked the same way regardless of hadActivity
            // so today is always visually findable, not just days with a completed session.
            val isToday = index == days.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(if (day.hadActivity || isToday) EvolaColors.Accent else EvolaColors.Surface)
                        .border(1.dp, if (day.hadActivity || isToday) Color.Transparent else EvolaColors.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.hadActivity || isToday) Color.White else EvolaColors.Text3,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isToday) EvolaColors.Accent else Color.Transparent))
            }
        }
    }
}

private fun dayInitial(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
}

private val fakeStatsWeeklyActivity = listOf(
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
private fun StatsSectionPreview() {
    EvolaTheme {
        StatsSection(
            progress = GoalProgress(
                overallPct = 0.42f, currentLessonId = "l1", streakDays = 5, todayCompleted = true,
                vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
                weeklyActivity = fakeStatsWeeklyActivity, bestStreakDays = 12, streakFreezesAvailable = 1,
            ),
        )
    }
}
