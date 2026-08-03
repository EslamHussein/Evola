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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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

        when {
            item.isFillBlank -> FillBlankDrill(item, onSubmit)
            item.isMultipleChoice -> MultipleChoiceDrill(item, onSubmit)
            else -> TypedRecallDrill(item, onSubmit)
        }
    }
}

@Composable
private fun MultipleChoiceDrill(item: VocabularySessionItem, onSubmit: (String, Boolean) -> Unit) {
    val prompt = if (item.isTermToMeaning) item.term else item.meaning
    Text(prompt, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))

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
}

@Composable
private fun TypedRecallDrill(item: VocabularySessionItem, onSubmit: (String, Boolean) -> Unit) {
    Text(item.term, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(32.dp))

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

/** Fill-in-the-blank drill: the term's own example sentence with the term itself replaced by a
 * blank, plus the grammar tag (part of speech / case) and an English translation as a hint - this
 * tests recalling the term in context, complementing typed-recall's meaning-from-term. */
@Composable
private fun FillBlankDrill(item: VocabularySessionItem, onSubmit: (String, Boolean) -> Unit) {
    Text(
        blankedSentence(item.sentenceWithBlank ?: item.term),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(12.dp))

    val tag = listOfNotNull(item.partOfSpeech, item.grammaticalCase).joinToString(", ")
    if (tag.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                tag,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    item.sentenceTranslation?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }

    var typedAnswer by remember(item.itemId) { mutableStateOf("") }
    OutlinedTextField(
        value = typedAnswer,
        onValueChange = { typedAnswer = it },
        label = { Text("Type the missing word") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onSubmit(typedAnswer, isTolerantMatch(item.term, typedAnswer)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Submit")
    }
}

private fun blankedSentence(sentence: String): AnnotatedString = buildAnnotatedString {
    val idx = sentence.indexOf("___")
    if (idx < 0) {
        append(sentence)
        return@buildAnnotatedString
    }
        append(sentence.substring(0, idx))
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("_____") }
    append(sentence.substring(idx + 3))
}
