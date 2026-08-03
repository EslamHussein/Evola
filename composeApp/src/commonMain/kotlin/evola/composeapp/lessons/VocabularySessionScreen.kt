@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.shared.vocabulary.VocabularySessionItem
import evola.shared.vocabulary.isTolerantMatch

/** Consolidated Session Start / Drill / Summary per 06_SCREENS_REFERENCE.md screens #13-15. */
@Composable
fun VocabularySessionScreen(viewModel: VocabularySessionViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Vocabulary session") }) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is VocabularySessionState.Loading -> CenteredMessage { CircularProgressIndicator() }

                is VocabularySessionState.Error -> CenteredMessage {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::retry) { Text("Retry") }
                }

                is VocabularySessionState.Empty -> CenteredMessage {
                    Text(
                        if (current.hasLessonVocabulary) {
                            "Nothing to review right now. Check back later or move on to the next lesson."
                        } else {
                            "This lesson doesn't have vocabulary yet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text("Done") }
                }

                is VocabularySessionState.InProgress -> DrillBody(
                    item = current.currentItem,
                    answeredCount = current.answeredCount,
                    onSubmit = viewModel::submitAnswer,
                )

                is VocabularySessionState.Summary -> CenteredMessage {
                    Text("Session complete!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${current.itemsCount} items - ${current.accuracy.toInt()}% correct",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun DrillBody(item: VocabularySessionItem, answeredCount: Int, onSubmit: (String, Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Answered: $answeredCount", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(24.dp))

        val prompt = if (item.isMultipleChoice) {
            if (item.isTermToMeaning) item.term else item.meaning
        } else {
            item.term
        }
        Text(prompt, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        if (item.isMultipleChoice) {
            val expected = if (item.isTermToMeaning) item.meaning else item.term
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.choices.forEach { choice ->
                    OutlinedButton(
                        onClick = { onSubmit(choice, choice == expected) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(choice)
                    }
                }
            }
        } else {
            var typedAnswer by remember(item.itemId) { mutableStateOf("") }
            OutlinedTextField(
                value = typedAnswer,
                onValueChange = { typedAnswer = it },
                label = { Text("Type the meaning") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSubmit(typedAnswer, isTolerantMatch(item.meaning, typedAnswer)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Submit")
            }
        }
    }
}
