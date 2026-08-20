package evola.composeapp.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_profile_achievements_title
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.profile.domain.ALL_BADGES
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Reword's achievement badges - a fixed grid of every [ALL_BADGES]
 * entry, locked/unlocked styled by [unlockedBadgeIds] rather than only showing what's earned so
 * far - seeing what's still ahead is part of the point. */
@Composable
internal fun AchievementsSection(unlockedBadgeIds: Set<String>) {
    Text(stringResource(Res.string.main_profile_achievements_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            ALL_BADGES.forEachIndexed { index, badge ->
                if (index > 0) {
                    Spacer(Modifier.height(EvolaSpacing.sm))
                }
                val unlocked = badge.id in unlockedBadgeIds
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (unlocked) EvolaColors.Gold else EvolaColors.Text3,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(EvolaSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            badge.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (unlocked) EvolaColors.Text else EvolaColors.Text3,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(badge.description, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AchievementsSectionPreview() {
    EvolaTheme {
        AchievementsSection(unlockedBadgeIds = ALL_BADGES.take(2).map { it.id }.toSet())
    }
}
