@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.CircularProgressRing
import evola.composeapp.theme.components.IconTile
import evola.composeapp.theme.components.LockedRow
import evola.shared.lessons.LessonDetail
import evola.shared.lessons.LessonSection
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_detail_loading
import evola.composeapp.generated.resources.lessons_detail_numbered_title
import evola.composeapp.generated.resources.lessons_detail_title
import evola.composeapp.generated.resources.lessons_detail_view_list
import evola.composeapp.generated.resources.lessons_nav_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onBack: () -> Unit,
    onOpenSection: (key: String) -> Unit,
    onViewVocabularyList: () -> Unit,
) {
    val state by viewModel.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_nav_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is LessonDetailState.Loading -> ProgressMessage(stringResource(Res.string.lessons_detail_loading))
                is LessonDetailState.Error -> CenteredMessage(current.message)
                is LessonDetailState.Loaded -> LessonDetailBody(
                    detail = current.detail,
                    onOpenSection = onOpenSection,
                    onViewVocabularyList = onViewVocabularyList,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun ProgressMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChaseLoadingIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LessonDetailBody(
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

private fun iconFor(key: String): ImageVector = when (key) {
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

@Composable
private fun SectionRow(section: LessonSection, onClick: () -> Unit, onViewList: () -> Unit) {
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
