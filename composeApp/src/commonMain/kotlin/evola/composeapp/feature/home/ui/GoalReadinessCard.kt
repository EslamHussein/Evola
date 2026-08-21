package evola.composeapp.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_exam_readiness
import evola.composeapp.generated.resources.main_home_your_goal_label
import evola.shared.feature.profile.domain.AppTheme
import org.jetbrains.compose.resources.stringResource

/** The Home dashboard's top card - merges what used to be two separate pieces ("Your goal", a
 * plain text block above the dashboard, and a standalone exam-readiness ring further down) into
 * one card split by a vertical divider: the goal on the left, [MiniReadinessRing] on the right.
 * [percent] is the same `progress.overallPct`-derived value the old standalone ring used, computed
 * in [DashboardBody]. */
@Composable
internal fun GoalReadinessCard(goalText: String, percent: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EvolaColors.Surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = EvolaSpacing.lg, horizontal = 18.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(Res.string.main_home_your_goal_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = EvolaColors.Accent,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(goalText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            VerticalDivider(
                modifier = Modifier.padding(vertical = EvolaSpacing.md),
                color = EvolaColors.Border,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = EvolaSpacing.lg, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiniReadinessRing(percent = percent)
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(
                    stringResource(Res.string.main_home_exam_readiness),
                    style = MaterialTheme.typography.labelSmall,
                    color = EvolaColors.Text3,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GoalReadinessCardLightPreview() {
    EvolaTheme(appTheme = AppTheme.LIGHT) {
        GoalReadinessCard(goalText = "Pass B1", percent = 62)
    }
}

@Preview
@Composable
private fun GoalReadinessCardDarkPreview() {
    EvolaTheme(appTheme = AppTheme.DARK) {
        GoalReadinessCard(goalText = "Pass B1", percent = 62)
    }
}
