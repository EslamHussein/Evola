@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.BackHandler
import evola.shared.grammar.GrammarExercise

/** Grammar's exercise session (01_PRODUCT_SPEC.md §1.9): a flat multiple-choice/fill-in-blank
 * list, unlike Vocabulary's pack/7-stage model. Exiting mid-drill is always safe - the session
 * resumes exactly where it left off (server-tracked via `grammar_session_answers`), so both the
 * back arrow and the system back gesture are wired to [onDone] in every state. */
@Composable
fun GrammarExerciseSessionScreen(viewModel: GrammarExerciseSessionViewModel, onDone: () -> Unit) {
    val state by viewModel.subscribe()
    BackHandler(onBack = onDone)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Grammar practice") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is GrammarExerciseSessionState.Loading -> CenteredMessage { ChaseLoadingIndicator() }

                is GrammarExerciseSessionState.Error -> CenteredMessage {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.intent(GrammarExerciseSessionIntent.Retry) }) { Text("Retry") }
                }

                is GrammarExerciseSessionState.Empty -> CenteredMessage {
                    Text(
                        "No exercises for this topic yet - check its explanation on the topic list.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text("Done") }
                }

                is GrammarExerciseSessionState.InProgress -> ExerciseBody(
                    exercise = current.currentExercise,
                    answeredCount = current.answeredCount,
                    onSubmit = { response, correct ->
                        viewModel.intent(GrammarExerciseSessionIntent.SubmitAnswer(current.currentExercise.exerciseId, response, correct))
                    },
                )

                is GrammarExerciseSessionState.Summary -> CenteredMessage {
                    Text("Practice complete!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${current.exercisesCompleted} exercises - ${current.accuracy.toInt()}% correct",
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
private fun ExerciseBody(exercise: GrammarExercise, answeredCount: Int, onSubmit: (String, Boolean) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() }
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text("Answered: $answeredCount", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(24.dp))

        if (exercise.isMultipleChoice) {
            MultipleChoiceDrill(exercise, onSubmit)
        } else {
            FillInBlankDrill(exercise, onSubmit)
        }
    }
}

@Composable
private fun MultipleChoiceDrill(exercise: GrammarExercise, onSubmit: (String, Boolean) -> Unit) {
    Text(blankedPrompt(exercise.prompt), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(32.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        exercise.choices.forEach { choice ->
            OutlinedButton(
                onClick = { onSubmit(choice, exercise.grade(choice)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(choice)
            }
        }
    }
}

@Composable
private fun FillInBlankDrill(exercise: GrammarExercise, onSubmit: (String, Boolean) -> Unit) {
    Text(blankedPrompt(exercise.prompt), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(32.dp))

    var typedAnswer by remember(exercise.exerciseId) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = typedAnswer,
        onValueChange = { typedAnswer = it },
        label = { Text("Type the missing word") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onSubmit(typedAnswer, exercise.grade(typedAnswer)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Submit")
    }
}

private fun blankedPrompt(prompt: String): AnnotatedString = buildAnnotatedString {
    val idx = prompt.indexOf("___")
    if (idx < 0) {
        append(prompt)
        return@buildAnnotatedString
    }
    append(prompt.substring(0, idx))
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("_____") }
    append(prompt.substring(idx + 3))
}
