package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.components.EvolaListRow
import evola.composeapp.core.designsystem.components.IconTile
import evola.composeapp.core.designsystem.components.LockedRow
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_detail_view_list
import evola.shared.feature.learning.domain.LessonSection
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SectionRow(section: LessonSection, onClick: () -> Unit, onViewList: () -> Unit) {
    if (section.locked) {
        LockedRow(label = section.label, subtitle = section.subtitle, icon = iconFor(section.key), lockIcon = Icons.Filled.Lock)
        return
    }

    // EvolaListRow's own clickable already carries role = Role.Button + onClickLabel = title (the
    // accessibility fix this row previously set by hand), so nothing extra is needed here to
    // preserve that semantics.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(EvolaColors.Surface),
    ) {
        EvolaListRow(
            title = section.label,
            subtitle = section.subtitle,
            leading = { IconTile(icon = iconFor(section.key), locked = false) },
            trailing = {
                Icon(
                    if (section.state == "done") Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (section.state == "done") EvolaColors.Gold else EvolaColors.Text3,
                )
            },
            onClick = onClick,
        )
        // Only Vocabulary has a separate list affordance - Grammar's topic list is the primary
        // destination reached by the row tap itself, not a secondary link.
        if (section.key == "vocabulary") {
            TextButton(onClick = onViewList, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(Res.string.lessons_detail_view_list), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
