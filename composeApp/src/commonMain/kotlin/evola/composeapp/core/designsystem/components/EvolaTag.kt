package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme

/** The app's one pill/chip - fixed `shape = MaterialTheme.shapes.extraLarge`, so every status/count/
 * category badge across the app converges on one pill treatment instead of each screen hand-rolling
 * its own `Surface(shape = extraLarge, ...)` block. [onClick] is null for a plain informational tag
 * (the common case); pass it only for a real trigger, e.g. a dropdown-opening pill. */
@Composable
fun EvolaTag(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = EvolaColors.SurfaceAlt,
    contentColor: Color = EvolaColors.Text2,
    border: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val borderStroke = border?.let { BorderStroke(1.dp, it) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = containerColor, border = borderStroke) {
            EvolaTagContent(label, icon, contentColor)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, border = borderStroke) {
            EvolaTagContent(label, icon, contentColor)
        }
    }
}

@Composable
private fun EvolaTagContent(label: String, icon: ImageVector?, contentColor: Color) {
    Row(
        modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(EvolaSpacing.xs))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

@Preview
@Composable
private fun EvolaTagPreview() {
    EvolaTheme {
        Column(modifier = Modifier.background(EvolaColors.Paper).padding(EvolaSpacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                EvolaTag(label = "New word", icon = Icons.Filled.Speed, containerColor = EvolaColors.AccentSoft, contentColor = EvolaColors.Accent)
                EvolaTag(label = "Related")
                EvolaTag(label = "Frequent", border = EvolaColors.Border, containerColor = EvolaColors.Surface)
            }
            Spacer(Modifier.width(EvolaSpacing.sm))
            EvolaTag(label = "Default voice", modifier = Modifier.padding(top = EvolaSpacing.sm), onClick = {})
        }
    }
}
