package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.VocabularyAnswerResult
import evola.shared.feature.vocabulary.domain.VocabularyCard
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Multiple-choice check for a [VocabularyCard.Practice] card, reachable via the grid icon instead
 * of the plain swipe. Grades on tap; correctness feeds the same SRS transition a graded swipe
 * would. */
@Composable
internal fun ChoiceCheck(
    card: VocabularyCard.Practice,
    answered: VocabularyAnswerResult?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var selected by remember(card.itemId, answered) { mutableStateOf<String?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        card.choices.forEach { choice ->
            val revealed = answered != null
            val isCorrectChoice = revealed && choice == answered.correctAnswer
            val isSelectedWrong = revealed && choice == selected && choice != answered.correctAnswer
            val (bg, border) = when {
                isCorrectChoice -> EvolaColors.GoldSoft to EvolaColors.Gold
                isSelectedWrong -> EvolaColors.RustSoft to EvolaColors.Rust
                else -> EvolaColors.Surface to EvolaColors.Border
            }
            Surface(
                onClick = {
                    if (!revealed) {
                        selected = choice
                        onSelect(choice)
                    }
                },
                enabled = !revealed,
                shape = MaterialTheme.shapes.extraLarge,
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Text(choice, modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm))
            }
        }
    }

    if (answered != null) {
        Spacer(Modifier.height(EvolaSpacing.md))
        FeedbackNote(answered)
    }

    AdvanceButton(answered, onContinue)
}

@Preview
@Composable
private fun ChoiceCheckPreview() {
    EvolaTheme {
        ChoiceCheck(card = fakePracticeCard, answered = null, onSelect = {}, onContinue = {})
    }
}
