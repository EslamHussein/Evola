package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_grammar_submit
import evola.composeapp.generated.resources.lessons_grammar_type_missing_word
import evola.shared.feature.learning.domain.GrammarExercise
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FillInBlankDrill(exercise: GrammarExercise, onSubmit: (String, Boolean) -> Unit) {
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

internal fun blankedPrompt(prompt: String): AnnotatedString = buildAnnotatedString {
    val idx = prompt.indexOf("___")
    if (idx < 0) {
        append(prompt)
        return@buildAnnotatedString
    }
    append(prompt.substring(0, idx))
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("_____") }
    append(prompt.substring(idx + 3))
}
