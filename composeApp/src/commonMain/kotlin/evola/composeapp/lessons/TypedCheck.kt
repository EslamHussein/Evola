package evola.composeapp.lessons

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_check
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Typed-recall check for a [VocabularyCard.Practice] card, reachable via the keyboard icon instead
 * of the plain swipe. Grades on "Check"; correctness feeds the same SRS transition a graded swipe
 * would. */
@Composable
internal fun TypedCheck(
    card: VocabularyCard.Practice,
    answered: VocabularyAnswerResult?,
    onCheck: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var typedAnswer by remember(card.itemId, answered) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    if (answered == null) {
        InlineFillSentence(
            prefix = "",
            suffix = "",
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            onDone = { focusManager.clearFocus() },
        )
    } else {
        RevealedInlineSentence(prefix = "", word = answered.correctAnswer ?: typedAnswer, suffix = "", correct = answered.correct == true)
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text(stringResource(Res.string.lessons_check))
        }
    } else {
        FeedbackNote(answered)
        AdvanceButton(answered, onContinue)
    }
}

@Preview
@Composable
private fun TypedCheckPreview() {
    EvolaTheme {
        TypedCheck(card = fakePracticeCard, answered = null, onCheck = {}, onContinue = {})
    }
}
