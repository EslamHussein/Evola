package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.components.CircularProgressRing
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_detail_numbered_title
import evola.shared.feature.learning.domain.LessonDetail
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LessonDetailBody(
    detail: LessonDetail,
    onOpenSection: (key: String) -> Unit,
    onViewVocabularyList: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(detail.breadcrumb, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                    Text(stringResource(Res.string.lessons_detail_numbered_title, detail.number, detail.title), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(EvolaSpacing.md))
                CircularProgressRing(percent = detail.progressPercent, size = 40.dp)
            }
            Spacer(Modifier.height(EvolaSpacing.lg))
        }
        items(detail.sections) { section ->
            SectionRow(
                section = section,
                onClick = { onOpenSection(section.key) },
                onViewList = onViewVocabularyList,
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
        }
    }
}

internal fun iconFor(key: String): ImageVector = when (key) {
    "vocabulary" -> Icons.AutoMirrored.Filled.MenuBook
    "grammar" -> Icons.AutoMirrored.Filled.Rule
    "reading" -> Icons.Filled.Edit
    "exercises" -> Icons.Filled.Quiz
    "speaking" -> Icons.Filled.Mic
    "writing" -> Icons.Filled.Create
    "review" -> Icons.Filled.Refresh
    "progress" -> Icons.AutoMirrored.Filled.TrendingUp
    else -> Icons.AutoMirrored.Filled.MenuBook
}
