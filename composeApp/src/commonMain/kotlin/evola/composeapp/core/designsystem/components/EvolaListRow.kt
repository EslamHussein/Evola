package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme

/** The app's one list-row layout - a leading slot (icon tile, avatar, or nothing), a title/subtitle
 * body, and a trailing slot (chevron, switch, stepper, pill), shared by settings rows, app-section
 * rows, and lesson section rows rather than each hand-rolling the same Row/Column skeleton.
 * [leading]/[trailing] stay slots (not a locked `ImageVector` param) since callers need arbitrary
 * content there (a [IconTile], a `Switch`, stepper buttons, a dropdown-trigger pill). When [onClick]
 * is non-null the row is wrapped in a real `Modifier.clickable` (with [Role.Button] semantics, a good
 * default for a tappable row) rather than a conditionally-included modifier, so [enabled] always
 * reflects the row's actual clickable state. */
@Composable
fun EvolaListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClickLabel = title, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(EvolaSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(EvolaSpacing.md))
            trailing()
        }
    }
}

@Preview
@Composable
private fun EvolaListRowPreview() {
    EvolaTheme {
        Column(modifier = Modifier.background(EvolaColors.Paper)) {
            EvolaListRow(
                title = "Settings",
                subtitle = "Notifications, appearance, and more",
                leading = { IconTile(Icons.Filled.Settings, locked = false) },
                trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EvolaColors.Text3) },
                onClick = {},
            )
            EvolaDivider()
            EvolaListRow(title = "No subtitle, no slots")
        }
    }
}
