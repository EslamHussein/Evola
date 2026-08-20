package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(EvolaColors.Surface)
            .clickable(onClickLabel = section.label, role = Role.Button, onClick = onClick)
            .padding(EvolaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
    ) {
        IconTile(icon = iconFor(section.key), locked = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(section.label, style = MaterialTheme.typography.titleSmall)
            Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            // Only Vocabulary has a separate list affordance - Grammar's topic list is the
            // primary destination reached by the row tap itself, not a secondary link.
            if (section.key == "vocabulary") {
                TextButton(onClick = onViewList, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.lessons_detail_view_list), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Icon(
            if (section.state == "done") Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (section.state == "done") EvolaColors.Gold else EvolaColors.Text3,
        )
    }
}
