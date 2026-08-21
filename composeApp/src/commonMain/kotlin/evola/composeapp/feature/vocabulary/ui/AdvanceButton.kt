package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_continue
import evola.composeapp.generated.resources.lessons_finish_session
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.EvolaButtonSize
import evola.composeapp.core.designsystem.components.EvolaPrimaryButton
import evola.shared.feature.vocabulary.domain.VocabularyAnswerResult
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun AdvanceButton(answered: VocabularyAnswerResult?, onContinue: () -> Unit) {
    if (answered == null) return
    Spacer(Modifier.height(EvolaSpacing.lg))
    EvolaPrimaryButton(
        text = if (answered.next == null) stringResource(Res.string.lessons_finish_session) else stringResource(Res.string.lessons_continue),
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
        size = EvolaButtonSize.Large,
    )
}

@Preview
@Composable
private fun AdvanceButtonPreview() {
    EvolaTheme {
        AdvanceButton(
            answered = VocabularyAnswerResult(correct = true, correctAnswer = "Hund", completedSentence = null, next = null, justMastered = false),
            onContinue = {},
        )
    }
}
