package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.shared.feature.learning.domain.GrammarExercise

@Composable
internal fun MultipleChoiceDrill(exercise: GrammarExercise, onSubmit: (String, Boolean) -> Unit) {
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
