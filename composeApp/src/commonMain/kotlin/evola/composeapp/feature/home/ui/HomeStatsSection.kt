package evola.composeapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import evola.composeapp.core.designsystem.components.EvolaCard
import evola.composeapp.core.designsystem.components.EvolaDivider
import evola.composeapp.core.designsystem.components.EvolaStatTile
import evola.shared.feature.onboarding.domain.DayActivity
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Reword's "Stats" section - two big Current/Best streak tiles and a Share row, all in one card.
 * [evola.composeapp.core.common.rememberShareText] is the same platform share sheet Profile's own
 * "Share progress" row uses. */
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
    EvolaCard(modifier = Modifier.fillMaxWidth()) {
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
        EvolaDivider()
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

@Composable
private fun StreakTile(label: String, days: Int, modifier: Modifier = Modifier) {
    val dayWord = stringResource(if (days == 1) Res.string.main_home_day_singular else Res.string.main_home_day_plural)
    EvolaStatTile(value = "$days $dayWord", label = label, modifier = modifier)
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
