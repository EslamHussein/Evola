@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.feature.learning.vm.LessonDetailState
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_detail_loading
import evola.composeapp.generated.resources.lessons_detail_title
import evola.composeapp.generated.resources.lessons_nav_back
import evola.shared.feature.learning.domain.LessonDetail
import evola.shared.feature.learning.domain.LessonSection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun LessonDetailContent(
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
