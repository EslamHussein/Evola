@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.materials_detail_back_desc
import evola.composeapp.generated.resources.materials_detail_delete_lesson_desc
import evola.composeapp.generated.resources.materials_detail_exercises_label
import evola.composeapp.generated.resources.materials_detail_grammar_label
import evola.composeapp.generated.resources.materials_detail_lesson_count_plural
import evola.composeapp.generated.resources.materials_detail_lesson_count_singular
import evola.composeapp.generated.resources.materials_detail_lesson_number_title
import evola.composeapp.generated.resources.materials_detail_lesson_words
import evola.composeapp.generated.resources.materials_detail_loading
import evola.composeapp.generated.resources.materials_detail_partial_success
import evola.composeapp.generated.resources.materials_detail_process_failed
import evola.composeapp.generated.resources.materials_detail_reading_label
import evola.composeapp.generated.resources.materials_detail_ready_of_total
import evola.composeapp.generated.resources.materials_detail_retry
import evola.composeapp.generated.resources.materials_detail_retry_rest
import evola.composeapp.generated.resources.materials_detail_splitting
import evola.composeapp.generated.resources.materials_detail_status_done
import evola.composeapp.generated.resources.materials_detail_status_extracting
import evola.composeapp.generated.resources.materials_detail_status_failed
import evola.composeapp.generated.resources.materials_detail_status_in_progress
import evola.composeapp.generated.resources.materials_detail_status_not_started
import evola.composeapp.generated.resources.materials_detail_status_waiting
import evola.composeapp.generated.resources.materials_detail_title
import evola.composeapp.generated.resources.materials_detail_tokens_used
import evola.composeapp.generated.resources.materials_detail_unsupported
import evola.composeapp.generated.resources.materials_detail_vocabulary_label
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.CircularProgressRing
import evola.composeapp.theme.components.StatusTag
import evola.composeapp.theme.components.StatusTagStyle
import evola.shared.materials.Lesson
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun MaterialDetailScreen(viewModel: MaterialDetailViewModel, onBack: () -> Unit, onOpenLesson: (String) -> Unit) {
    val state by viewModel.subscribe()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.materials_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.materials_detail_back_desc))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is MaterialDetailState.Loading -> ProgressMessage(stringResource(Res.string.materials_detail_loading))
                is MaterialDetailState.Error -> CenteredMessage(current.message)
                is MaterialDetailState.Loaded -> LoadedBody(
                    current.detail,
                    onRetry = { viewModel.intent(MaterialDetailIntent.Retry) },
                    onOpenLesson = onOpenLesson,
                    onDeleteLesson = { lessonId -> viewModel.intent(MaterialDetailIntent.DeleteLesson(lessonId)) },
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

/** Same as [CenteredMessage] but with a visible spinner above it, for states where real work is
 * happening in the background (upload processing, lesson segmentation) rather than just a static wait. */
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
private fun LoadedBody(detail: MaterialDetail, onRetry: () -> Unit, onOpenLesson: (String) -> Unit, onDeleteLesson: (String) -> Unit) {
    when (detail.material.status) {
        MaterialStatus.UPLOADED, MaterialStatus.PROCESSING ->
            if (detail.lessons.isEmpty()) {
                ProgressMessage(stringResource(Res.string.materials_detail_splitting, detail.material.filename))
            } else {
                InProgressBody(detail, onOpenLesson, onDeleteLesson)
            }

        MaterialStatus.FAILED ->
            if (detail.lessons.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.materials_detail_process_failed, detail.material.filename),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRetry) { Text(stringResource(Res.string.materials_detail_retry)) }
                    }
                }
            } else {
                PartialSuccessBody(detail, onRetry, onOpenLesson, onDeleteLesson)
            }

        MaterialStatus.UNSUPPORTED_CONTENT ->
            CenteredMessage(stringResource(Res.string.materials_detail_unsupported, detail.material.filename))

        MaterialStatus.READY -> ResourceDetailBody(detail, onOpenLesson, onDeleteLesson)
    }
}

@Composable
private fun ResourceDetailBody(detail: MaterialDetail, onOpenLesson: (String) -> Unit, onDeleteLesson: (String) -> Unit) {
    val lessons = detail.lessons
    val overallProgress = if (lessons.isEmpty()) 0 else (lessons.map { it.completionPct }.average() * 100).toInt()
    val vocabTotal = lessons.sumOf { it.vocabCount }
    val grammarTotal = lessons.sumOf { it.grammarCount }
    val readingTotal = lessons.sumOf { it.readingCount }
    val exercisesTotal = lessons.sumOf { it.exercisesCount }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        item {
            BookHeaderRow(detail, overallProgress)
            Spacer(Modifier.height(EvolaSpacing.md))
            MetaStatRow(vocabTotal, grammarTotal, readingTotal, exercisesTotal)
            Spacer(Modifier.height(EvolaSpacing.lg))
            Text(lessonCountLabel(lessons.size), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.sm))
        }
        items(lessons, key = { it.id }) { lesson ->
            LessonRow(lesson, onClick = { onOpenLesson(lesson.id) }, onDelete = { onDeleteLesson(lesson.id) })
        }
    }
}

/** Live per-lesson progress while `PROCESSING` with lesson rows already present - a trimmed
 * [ResourceDetailBody] (no completion ring/meta-stat row, since vocab/grammar progress isn't
 * meaningful mid-extraction) showing "$ready of $total lessons ready" instead. Reuses [LessonRow]
 * as-is - it already renders "extracting"/"failed"/"pending" states correctly. */
@Composable
private fun InProgressBody(detail: MaterialDetail, onOpenLesson: (String) -> Unit, onDeleteLesson: (String) -> Unit) {
    val lessons = detail.lessons
    val readyCount = lessons.count { it.status == "ready" }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        item {
            Text(detail.material.filename, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.materials_detail_ready_of_total, readyCount, lessons.size),
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(EvolaSpacing.lg))
        }
        items(lessons, key = { it.id }) { lesson ->
            LessonRow(lesson, onClick = { onOpenLesson(lesson.id) }, onDelete = { onDeleteLesson(lesson.id) })
        }
    }
}

@Composable
private fun BookHeaderRow(detail: MaterialDetail, overallProgress: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(EvolaColors.GoldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = EvolaColors.Gold)
        }
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(detail.material.filename, style = MaterialTheme.typography.titleMedium)
            Text(
                lessonCountLabel(detail.lessons.size),
                style = MaterialTheme.typography.bodySmall,
                color = EvolaColors.Text2,
            )
            val totalTokens = detail.material.inputTokens + detail.material.outputTokens
            if (totalTokens > 0) {
                Text(
                    stringResource(Res.string.materials_detail_tokens_used, formatTokenCount(totalTokens)),
                    style = MaterialTheme.typography.labelSmall,
                    color = EvolaColors.Text3,
                )
            }
        }
        Spacer(Modifier.width(EvolaSpacing.md))
        CircularProgressRing(percent = overallProgress)
    }
}

/** "1.2K"/"3.4M"-style compact count - a raw token total easily runs into 5-6 digits for a
 * multi-lesson material, which reads as noise next to the lesson-count line above it. */
private fun formatTokenCount(count: Long): String = when {
    count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
    count >= 1_000 -> "${(count / 100) / 10.0}K"
    else -> count.toString()
}

@Composable
private fun MetaStatRow(vocab: Int, grammar: Int, reading: Int, exercises: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MetaStat(Icons.AutoMirrored.Filled.MenuBook, "$vocab", stringResource(Res.string.materials_detail_vocabulary_label))
        MetaStat(Icons.AutoMirrored.Filled.Rule, "$grammar", stringResource(Res.string.materials_detail_grammar_label))
        MetaStat(Icons.Filled.Edit, "$reading", stringResource(Res.string.materials_detail_reading_label))
        MetaStat(Icons.Filled.Quiz, "$exercises", stringResource(Res.string.materials_detail_exercises_label))
    }
}

@Composable
private fun MetaStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = EvolaColors.Text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
    }
}

@Composable
private fun PartialSuccessBody(detail: MaterialDetail, onRetry: () -> Unit, onOpenLesson: (String) -> Unit, onDeleteLesson: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                stringResource(
                    Res.string.materials_detail_partial_success,
                    lessonCountLabel(detail.lessons.size),
                    detail.material.filename,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text(stringResource(Res.string.materials_detail_retry_rest)) }
            Spacer(Modifier.height(16.dp))
        }
        items(detail.lessons, key = { it.id }) { lesson ->
            LessonRow(lesson, onClick = { onOpenLesson(lesson.id) }, onDelete = { onDeleteLesson(lesson.id) })
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, onClick: () -> Unit, onDelete: () -> Unit) {
    val (tagLabel, tagStyle) = when {
        lesson.status == "ready" && lesson.completionPct >= 1f -> stringResource(Res.string.materials_detail_status_done) to StatusTagStyle.FILLED
        lesson.status == "ready" && lesson.completionPct > 0f -> stringResource(Res.string.materials_detail_status_in_progress) to StatusTagStyle.OUTLINE
        lesson.status == "ready" -> stringResource(Res.string.materials_detail_status_not_started) to StatusTagStyle.NEUTRAL
        lesson.status == "extracting" -> stringResource(Res.string.materials_detail_status_extracting) to StatusTagStyle.OUTLINE
        lesson.status == "failed" -> stringResource(Res.string.materials_detail_status_failed) to StatusTagStyle.OUTLINE
        else -> stringResource(Res.string.materials_detail_status_waiting) to StatusTagStyle.NEUTRAL // "pending"
    }
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { it != SwipeToDismissBoxValue.StartToEnd })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(EvolaColors.Rust),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.padding(horizontal = EvolaSpacing.lg)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.materials_detail_delete_lesson_desc), tint = Color.White)
                }
            }
        },
    ) {
        // ElevatedCard, not the flat default Card - on this dark, tightly-stepped tonal palette,
        // a real drop shadow reads more clearly as "this row is a distinct surface" than the
        // tonal-color difference alone (M3's guidance for when elevation should carry that job).
        ElevatedCard(
            onClick = onClick,
            enabled = lesson.status == "ready",
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.materials_detail_lesson_number_title, lesson.number, lesson.title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    Text(
                        stringResource(Res.string.materials_detail_lesson_words, lesson.vocabCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = EvolaColors.Text3,
                    )
                }
                StatusTag(tagLabel, tagStyle)
            }
        }
    }
}

@Composable
private fun lessonCountLabel(count: Int): String =
    if (count == 1) {
        stringResource(Res.string.materials_detail_lesson_count_singular)
    } else {
        stringResource(Res.string.materials_detail_lesson_count_plural, count)
    }
