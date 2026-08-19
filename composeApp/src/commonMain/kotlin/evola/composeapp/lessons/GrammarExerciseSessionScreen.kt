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
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.BackHandler
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_done
import evola.composeapp.generated.resources.lessons_action_retry
import evola.composeapp.generated.resources.lessons_grammar_answered_count
import evola.composeapp.generated.resources.lessons_grammar_complete
import evola.composeapp.generated.resources.lessons_grammar_empty
import evola.composeapp.generated.resources.lessons_grammar_practice_title
import evola.composeapp.generated.resources.lessons_grammar_submit
import evola.composeapp.generated.resources.lessons_grammar_summary
import evola.composeapp.generated.resources.lessons_grammar_type_missing_word
import evola.composeapp.generated.resources.lessons_nav_back
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import evola.shared.grammar.GrammarExercise

/** Grammar's exercise session (01_PRODUCT_SPEC.md §1.9): a flat multiple-choice/fill-in-blank
 * list, unlike Vocabulary's pack/7-stage model. Exiting mid-drill is always safe - the session
 * resumes exactly where it left off (server-tracked via `grammar_session_answers`), so both the
 * back arrow and the system back gesture are wired to [onDone] in every state. */
@Composable
fun GrammarExerciseSessionScreen(viewModel: GrammarExerciseSessionViewModel, onDone: () -> Unit) {
    val state by viewModel.collectAsState()
    BackHandler(onBack = onDone)
    GrammarExerciseSessionContent(
        state = state,
        onDone = onDone,
        onRetry = { viewModel.retry() },
        onSubmit = { exerciseId, response, correct -> viewModel.submitAnswer(exerciseId, response, correct) },
    )
}

@Composable
private fun GrammarExerciseSessionContent(
    state: GrammarExerciseSessionState,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: (exerciseId: String, response: String, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_grammar_practice_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_nav_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is GrammarExerciseSessionState.Loading -> CenteredMessage { ChaseLoadingIndicator() }

                is GrammarExerciseSessionState.Error -> CenteredMessage {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(EvolaSpacing.lg))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.lessons_action_retry)) }
                }

                is GrammarExerciseSessionState.Empty -> CenteredMessage {
                    Text(
                        stringResource(Res.string.lessons_grammar_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(EvolaSpacing.lg))
                    Button(onClick = onDone) { Text(stringResource(Res.string.lessons_action_done)) }
                }

                is GrammarExerciseSessionState.InProgress -> ExerciseBody(
                    exercise = state.currentExercise,
                    answeredCount = state.answeredCount,
                    onSubmit = { response, correct ->
                        onSubmit(state.currentExercise.exerciseId, response, correct)
                    },
                )

                is GrammarExerciseSessionState.Summary -> CenteredMessage {
                    Text(stringResource(Res.string.lessons_grammar_complete), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(EvolaSpacing.sm))
                    Text(
                        stringResource(Res.string.lessons_grammar_summary, state.exercisesCompleted, state.accuracy.toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(EvolaSpacing.lg))
                    Button(onClick = onDone) { Text(stringResource(Res.string.lessons_action_done)) }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
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
            .padding(EvolaSpacing.xl),
    ) {
        Text(stringResource(Res.string.lessons_grammar_answered_count, answeredCount), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(EvolaSpacing.xl))

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
    Spacer(Modifier.height(EvolaSpacing.xxl))

    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
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
    Spacer(Modifier.height(EvolaSpacing.xxl))

    var typedAnswer by remember(exercise.exerciseId) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = typedAnswer,
        onValueChange = { typedAnswer = it },
        label = { Text(stringResource(Res.string.lessons_grammar_type_missing_word)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(EvolaSpacing.lg))
    Button(
        onClick = { onSubmit(typedAnswer, exercise.grade(typedAnswer)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.lessons_grammar_submit))
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

private val fakeMultipleChoiceExercise = GrammarExercise(
    exerciseId = "e1", type = "multiple_choice", prompt = "Ich sehe ___ Hund.", answerKey = "den",
    choices = listOf("der", "den", "dem", "des"),
)

private val fakeFillInBlankExercise = GrammarExercise(
    exerciseId = "e2", type = "fill_in_blank", prompt = "Ich sehe ___ Hund.", answerKey = "den",
)

@Preview
@Composable
private fun GrammarExerciseSessionLoadingPreview() {
    EvolaTheme { GrammarExerciseSessionContent(state = GrammarExerciseSessionState.Loading, onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> }) }
}

@Preview
@Composable
private fun GrammarExerciseSessionMultipleChoicePreview() {
    EvolaTheme {
        GrammarExerciseSessionContent(
            state = GrammarExerciseSessionState.InProgress(fakeMultipleChoiceExercise, answeredCount = 2),
            onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> },
        )
    }
}

@Preview
@Composable
private fun GrammarExerciseSessionFillInBlankPreview() {
    EvolaTheme {
        GrammarExerciseSessionContent(
            state = GrammarExerciseSessionState.InProgress(fakeFillInBlankExercise, answeredCount = 3),
            onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> },
        )
    }
}

@Preview
@Composable
private fun GrammarExerciseSessionEmptyPreview() {
    EvolaTheme { GrammarExerciseSessionContent(state = GrammarExerciseSessionState.Empty, onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> }) }
}

@Preview
@Composable
private fun GrammarExerciseSessionSummaryPreview() {
    EvolaTheme {
        GrammarExerciseSessionContent(
            state = GrammarExerciseSessionState.Summary(exercisesCompleted = 8, accuracy = 87.5),
            onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> },
        )
    }
}

@Preview
@Composable
private fun GrammarExerciseSessionErrorPreview() {
    EvolaTheme {
        GrammarExerciseSessionContent(state = GrammarExerciseSessionState.Error("Something went wrong."), onDone = {}, onRetry = {}, onSubmit = { _, _, _ -> })
    }
}
