@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.designsystem.CenteredMessage
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.EvolaErrorState
import evola.composeapp.feature.learning.vm.GrammarExerciseSessionState
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_done
import evola.composeapp.generated.resources.lessons_grammar_complete
import evola.composeapp.generated.resources.lessons_grammar_empty
import evola.composeapp.generated.resources.lessons_grammar_practice_title
import evola.composeapp.generated.resources.lessons_grammar_summary
import evola.composeapp.generated.resources.lessons_nav_back
import evola.shared.feature.learning.domain.GrammarExercise
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun GrammarExerciseSessionContent(
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

                is GrammarExerciseSessionState.Error -> EvolaErrorState(message = state.message, onRetry = onRetry)

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
