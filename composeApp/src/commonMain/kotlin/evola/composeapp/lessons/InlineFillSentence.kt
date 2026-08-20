package evola.composeapp.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Typed recall rendered inline within the sentence itself - the blank IS the text field, styled
 * to match the surrounding headline text, rather than a separate boxed Material field below. */
@Composable
internal fun InlineFillSentence(
    prefix: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val style = MaterialTheme.typography.headlineSmall
    val underlineColor = EvolaColors.Accent
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text(prefix, style = style)
        Box(
            modifier = Modifier.widthIn(min = 56.dp).drawBehind {
                drawLine(
                    color = underlineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style.copy(color = EvolaColors.Accent, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(EvolaColors.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.widthIn(min = 56.dp).padding(bottom = 2.dp),
            )
        }
        Text(suffix, style = style)
    }
}

/** Answer revealed inline (correct term highlighted within the sentence) once graded, matching
 * [InlineFillSentence]'s layout so the sentence doesn't visually jump between typing and reveal. */
@Composable
internal fun RevealedInlineSentence(prefix: String, word: String, suffix: String, correct: Boolean) {
    val color = if (correct) EvolaColors.Gold else EvolaColors.Rust
    Text(
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(word) }
            append(suffix)
        },
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Preview
@Composable
private fun InlineFillSentencePreview() {
    EvolaTheme {
        var value by remember { mutableStateOf("Hun") }
        InlineFillSentence(prefix = "Der ", suffix = " läuft schnell.", value = value, onValueChange = { value = it }, onDone = {})
    }
}

@Preview
@Composable
private fun RevealedInlineSentencePreview() {
    EvolaTheme {
        RevealedInlineSentence(prefix = "Der ", word = "Hund", suffix = " läuft schnell.", correct = true)
    }
}
