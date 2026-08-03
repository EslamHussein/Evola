@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.shared.materials.Exercise
import evola.shared.materials.GrammarRule
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.VocabItem

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
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Same as [CenteredMessage] but with a visible spinner above it, for states where real work is
 * happening in the background (upload processing, AI extraction) rather than just a static wait. */
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
        MaterialStatus.UPLOADED, MaterialStatus.ANALYZING ->
            ProgressMessage("Analyzing \"${detail.material.filename}\"...")
        MaterialStatus.FAILED ->
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
        MaterialStatus.NEEDS_REVIEW ->
            CenteredMessage("\"${detail.material.filename}\" needs review.")
        MaterialStatus.ANALYZED -> {
            val extraction = detail.extraction
            if (extraction == null) {
                CenteredMessage("No extracted content available.")
            } else {
                ExtractionBody(
                    vocabulary = extraction.vocabulary,
                    grammar = extraction.grammar,
                    exercises = extraction.exercises,
                )
            }
        }
    }
}

@Composable
private fun ExtractionBody(vocabulary: List<VocabItem>, grammar: List<GrammarRule>, exercises: List<Exercise>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { SectionHeader("Vocabulary") }
        items(vocabulary) { VocabRow(it) }

        item { Spacer(Modifier.height(16.dp)); SectionHeader("Grammar") }
        items(grammar) { GrammarRow(it) }

        item { Spacer(Modifier.height(16.dp)); SectionHeader("Exercises") }
        items(exercises) { ExerciseRow(it) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun VocabRow(item: VocabItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${item.term} — ${item.translation}", style = MaterialTheme.typography.titleSmall)
            item.exampleSentence?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GrammarRow(rule: GrammarRule) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(rule.topic, style = MaterialTheme.typography.titleSmall)
            Text(rule.explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.kind, style = MaterialTheme.typography.labelMedium)
            Text(exercise.prompt, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
