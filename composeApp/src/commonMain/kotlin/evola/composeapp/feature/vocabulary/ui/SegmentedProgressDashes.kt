package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** Reword's segmented progress strip - one dash per distinct word in the session (matching the
 * already-shown "Word X of Y" text), filled up to [filled]. Reads at a glance far better than a
 * single continuous bar once there are only a handful of words. Unfilled segments use [EvolaColors.Border]
 * rather than [EvolaColors.AccentSoft] - AccentSoft sits too close in lightness to the page
 * background to read at a glance (confirmed via a live screenshot); Border is the token already
 * meant for "visible against the page" hairlines. */
@Composable
internal fun SegmentedProgressDashes(total: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) EvolaColors.Accent else EvolaColors.Border),
            )
        }
    }
}

@Preview
@Composable
private fun SegmentedProgressDashesPreview() {
    EvolaTheme {
        SegmentedProgressDashes(total = 8, filled = 3, modifier = Modifier.fillMaxWidth())
    }
}
