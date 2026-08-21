package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.shared.feature.learning.domain.GrammarExercise
import kotlinx.coroutines.delay

/** Reveal delay (ms) between tapping a choice and advancing to the next exercise - long enough to
 * register the correct/incorrect coloring, matching [ChoiceCheck]'s Gold/Rust reveal convention. */
private const val REVEAL_DELAY_MS = 500L

@Composable
internal fun MultipleChoiceDrill(exercise: GrammarExercise, onSubmit: (String, Boolean) -> Unit) {
    // Keyed by exerciseId so a fresh question always starts unrevealed, even though this
    // composable instance is reused as the current exercise changes underneath it.
    var selected by remember(exercise.exerciseId) { mutableStateOf<String?>(null) }
    val revealed = selected != null

    LaunchedEffect(exercise.exerciseId, selected) {
        val choice = selected ?: return@LaunchedEffect
        delay(REVEAL_DELAY_MS)
        onSubmit(choice, exercise.grade(choice))
    }

    Text(blankedPrompt(exercise.prompt), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(EvolaSpacing.xxl))

    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        exercise.choices.forEach { choice ->
            val isCorrectChoice = revealed && exercise.grade(choice)
            val isSelectedWrong = revealed && choice == selected && !exercise.grade(choice)
            val (bg, border) = when {
                isCorrectChoice -> EvolaColors.GoldSoft to EvolaColors.Gold
                isSelectedWrong -> EvolaColors.RustSoft to EvolaColors.Rust
                else -> EvolaColors.Surface to EvolaColors.Border
            }
            Surface(
                onClick = { if (!revealed) selected = choice },
                enabled = !revealed,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Text(choice, modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm))
            }
        }
    }
}
