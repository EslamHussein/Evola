package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** 1dp hairline in [EvolaColors.Border], built on M3's real [HorizontalDivider] rather than a
 * hand-rolled `Surface`/`Box` rectangle - replaces every screen's own inline divider box with one
 * shared token-driven line. */
@Composable
fun EvolaDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = EvolaColors.Border, thickness = 1.dp)
}

@Preview
@Composable
private fun EvolaDividerPreview() {
    EvolaTheme {
        Column(modifier = Modifier.background(EvolaColors.Paper).padding(EvolaSpacing.lg)) {
            EvolaDivider()
        }
    }
}
