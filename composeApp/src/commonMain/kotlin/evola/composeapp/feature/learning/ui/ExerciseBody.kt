package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_grammar_answered_count
import evola.shared.feature.learning.domain.GrammarExercise
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExerciseBody(exercise: GrammarExercise, answeredCount: Int, onSubmit: (String, Boolean) -> Unit) {
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
