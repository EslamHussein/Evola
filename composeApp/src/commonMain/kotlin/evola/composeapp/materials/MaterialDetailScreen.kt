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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.CircularProgressRing
import evola.composeapp.theme.components.StatusTag
import evola.composeapp.theme.components.StatusTagStyle
import evola.shared.materials.Lesson
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus

@Composable
fun MaterialDetailScreen(viewModel: MaterialDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Material") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is MaterialDetailState.Loading -> ProgressMessage("Loading...")
                is MaterialDetailState.Error -> CenteredMessage(current.message)
                is MaterialDetailState.Loaded -> LoadedBody(current.detail, onRetry = viewModel::retry)
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
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LoadedBody(detail: MaterialDetail, onRetry: () -> Unit) {
    when (detail.material.status) {
        MaterialStatus.UPLOADED, MaterialStatus.PROCESSING ->
            ProgressMessage("Splitting \"${detail.material.filename}\" into lessons...")

        MaterialStatus.FAILED ->
            if (detail.lessons.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "We couldn't process \"${detail.material.filename}\".",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            } else {
                PartialSuccessBody(detail, onRetry)
            }

        MaterialStatus.UNSUPPORTED_CONTENT ->
            CenteredMessage("\"${detail.material.filename}\" doesn't look like readable text we can use. Try a different file.")

        MaterialStatus.READY -> ResourceDetailBody(detail)
    }
}

@Composable
private fun ResourceDetailBody(detail: MaterialDetail) {
    val lessons = detail.lessons
    val overallProgress = if (lessons.isEmpty()) 0 else (lessons.map { it.vocabProgress }.average() * 100).toInt()
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
        items(lessons) { LessonRow(it) }
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
        }
        Spacer(Modifier.width(EvolaSpacing.md))
        CircularProgressRing(percent = overallProgress)
    }
}

@Composable
private fun MetaStatRow(vocab: Int, grammar: Int, reading: Int, exercises: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MetaStat(Icons.AutoMirrored.Filled.MenuBook, "$vocab", "Vocabulary")
        MetaStat(Icons.AutoMirrored.Filled.Rule, "$grammar", "Grammar")
        MetaStat(Icons.Filled.Edit, "$reading", "Reading")
        MetaStat(Icons.Filled.Quiz, "$exercises", "Exercises")
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
private fun PartialSuccessBody(detail: MaterialDetail, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "We generated ${lessonCountLabel(detail.lessons.size)} from \"${detail.material.filename}\", " +
                    "but some of it couldn't be processed.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry the rest") }
            Spacer(Modifier.height(16.dp))
        }
        items(detail.lessons) { LessonRow(it) }
    }
}

@Composable
private fun LessonRow(lesson: Lesson) {
    val (tagLabel, tagStyle) = when {
        lesson.status == "ready" && lesson.vocabProgress >= 1f -> "Fertig" to StatusTagStyle.FILLED
        lesson.status == "ready" && lesson.vocabProgress > 0f -> "Läuft" to StatusTagStyle.OUTLINE
        lesson.status == "ready" -> "Offen" to StatusTagStyle.NEUTRAL
        else -> "Offen" to StatusTagStyle.NEUTRAL
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${lesson.number}. ${lesson.title}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(
                    "${lesson.vocabCount} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = EvolaColors.Text3,
                )
            }
            StatusTag(tagLabel, tagStyle)
        }
    }
}

private fun lessonCountLabel(count: Int): String = if (count == 1) "1 lesson" else "$count lessons"
