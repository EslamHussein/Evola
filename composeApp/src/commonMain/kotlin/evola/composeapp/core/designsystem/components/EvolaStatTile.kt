package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme

/** The app's one value/label stat block (a streak count, a session-summary stat, a lesson meta
 * count) - an optional [icon], a big [value] ([MaterialTheme.typography.titleLarge]), and a small
 * caption [label] ([EvolaColors.Text3]). [filled] = true wraps the content in a
 * `Surface(SurfaceAlt, shapes.medium)` for a standalone tile (e.g. a streak card); false renders a
 * plain `Column` with no background for one stat among several inline in a row (e.g. a lesson's
 * vocab/grammar/reading/exercise counts), where the row itself already establishes the container. */
@Composable
fun EvolaStatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = true) {
    if (filled) {
        Surface(modifier = modifier, color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.medium) {
            EvolaStatTileContent(value, label, icon, Modifier.padding(EvolaSpacing.md))
        }
    } else {
        EvolaStatTileContent(value, label, icon, modifier)
    }
}

@Composable
private fun EvolaStatTileContent(value: String, label: String, icon: ImageVector?, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = EvolaColors.Text2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(EvolaSpacing.xs))
        }
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
    }
}

@Preview
@Composable
private fun EvolaStatTilePreview() {
    EvolaTheme {
        Row(modifier = Modifier.background(EvolaColors.Paper), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            EvolaStatTile(value = "5", label = "Current streak")
            EvolaStatTile(value = "12", label = "Vocabulary", icon = Icons.AutoMirrored.Filled.MenuBook, filled = false)
        }
    }
}
