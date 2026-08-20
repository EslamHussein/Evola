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
import evola.composeapp.core.common.ChaseLoadingIndicator
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
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.CircularProgressRing
import evola.composeapp.core.designsystem.components.IconTile
import evola.composeapp.core.designsystem.components.LockedRow
import evola.shared.lessons.LessonDetail
import evola.shared.lessons.LessonSection
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_detail_loading
import evola.composeapp.generated.resources.lessons_detail_numbered_title
import evola.composeapp.generated.resources.lessons_detail_title
import evola.composeapp.generated.resources.lessons_detail_view_list
import evola.composeapp.generated.resources.lessons_nav_back
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onBack: () -> Unit,
    onOpenSection: (key: String) -> Unit,
    onViewVocabularyList: () -> Unit,
) {
    val state by viewModel.collectAsState()
    LessonDetailContent(state = state, onBack = onBack, onOpenSection = onOpenSection, onViewVocabularyList = onViewVocabularyList)
}

@Composable
private fun LessonDetailContent(
    state: LessonDetailState,
    onBack: () -> Unit,
    onOpenSection: (key: String) -> Unit,
    onViewVocabularyList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
            when (state) {
                is LessonDetailState.Loading -> ProgressMessage(stringResource(Res.string.lessons_detail_loading))
                is LessonDetailState.Error -> CenteredMessage(state.message)
                is LessonDetailState.Loaded -> LessonDetailBody(
                    detail = state.detail,
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
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(EvolaSpacing.xl))
    }
}

@Composable
private fun ProgressMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChaseLoadingIndicator()
            Spacer(Modifier.height(EvolaSpacing.lg))
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

private val fakeLessonDetail = LessonDetail(
    lessonId = "l1", number = 1, title = "Lesson 1", status = "ready",
    breadcrumb = "Grammar Book · Lesson 1", progressPercent = 45,
    sections = listOf(
        LessonSection(key = "vocabulary", label = "Vocabulary", subtitle = "12 words", locked = false, state = "in_progress"),
        LessonSection(key = "grammar", label = "Grammar", subtitle = "3 topics", locked = false, state = "done"),
        LessonSection(key = "reading", label = "Reading", subtitle = "Not built yet", locked = true, state = "locked"),
    ),
)

@Preview
@Composable
private fun LessonDetailLoadingPreview() {
    EvolaTheme { LessonDetailContent(state = LessonDetailState.Loading, onBack = {}, onOpenSection = {}, onViewVocabularyList = {}) }
}

@Preview
@Composable
private fun LessonDetailLoadedPreview() {
    EvolaTheme {
        LessonDetailContent(state = LessonDetailState.Loaded(fakeLessonDetail), onBack = {}, onOpenSection = {}, onViewVocabularyList = {})
    }
}

@Preview
@Composable
private fun LessonDetailErrorPreview() {
    EvolaTheme { LessonDetailContent(state = LessonDetailState.Error("Something went wrong."), onBack = {}, onOpenSection = {}, onViewVocabularyList = {}) }
}
