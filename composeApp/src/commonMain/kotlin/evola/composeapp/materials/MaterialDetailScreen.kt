@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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

        MaterialStatus.READY -> LessonsReadyBody(detail.lessons)
    }
}

@Composable
private fun LessonsReadyBody(lessons: List<Lesson>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(lessonCountLabel(lessons.size), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
        }
        items(lessons) { LessonRow(it) }
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
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "${lesson.number}. ${lesson.title}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun lessonCountLabel(count: Int): String = if (count == 1) "1 lesson" else "$count lessons"
