package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_feedback_correct
import evola.composeapp.generated.resources.lessons_feedback_incorrect
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.VocabularyAnswerResult
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun FeedbackNote(answered: VocabularyAnswerResult) {
    val correct = answered.correct ?: return
    val (bg, text) = if (correct) {
        EvolaColors.GoldSoft to stringResource(Res.string.lessons_feedback_correct)
    } else {
        EvolaColors.RustSoft to stringResource(Res.string.lessons_feedback_incorrect, answered.correctAnswer ?: "")
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            answered.completedSentence?.let {
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            }
        }
    }
}

@Preview
@Composable
private fun FeedbackNotePreview() {
    EvolaTheme {
        FeedbackNote(
            VocabularyAnswerResult(correct = false, correctAnswer = "Hund", completedSentence = "Der Hund läuft schnell.", next = null),
        )
    }
}
