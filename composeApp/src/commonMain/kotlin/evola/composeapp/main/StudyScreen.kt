@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.shared.goals.Lesson

/** Study tab per 06_SCREENS_REFERENCE.md screen #11 (Lesson Selection List, 01_PRODUCT_SPEC.md
 * §1.7): sequential, ungated list of every lesson across all of the goal's materials. A lesson
 * only becomes tappable once M6/M7 flips it to "ready" (real vocab/grammar content exists) -
 * [onOpenLesson] is already wired for that, but nothing routes there yet since the Lesson Home
 * hub (screen #12) is out of scope until then. */
@Composable
fun StudyScreen(viewModel: LessonSelectionViewModel, onOpenLesson: (Lesson) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Study") }) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is LessonSelectionState.Loading -> ProgressMessage("Loading lessons...")
                is LessonSelectionState.Error -> ErrorBody(current.message, onRetry = viewModel::refresh)
                is LessonSelectionState.Loaded -> LoadedBody(current.lessons, onOpenLesson)
            }
        }
    }
}

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
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun LoadedBody(lessons: List<Lesson>, onOpenLesson: (Lesson) -> Unit) {
    if (lessons.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Lessons aren't ready yet. Upload a material on the Materials tab to get started.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // Current lesson = first lesson in sequence not yet at 100% (01_PRODUCT_SPEC.md §1.7).
    val currentIndex = lessons.indexOfFirst { it.completionPct < 100f }
    if (currentIndex == -1) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("All lessons complete!", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Upload another material to keep learning.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lessons) { index, lesson ->
            LessonRow(lesson = lesson, isCurrent = index == currentIndex, onClick = { onOpenLesson(lesson) })
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, isCurrent: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = lesson.isReady,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${lesson.number}. ${lesson.title}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (lesson.isReady) "${lesson.completionPct.toInt()}% complete" else "Still preparing...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent && lesson.isReady) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
