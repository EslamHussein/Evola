package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.misc_back_to_lesson
import evola.composeapp.generated.resources.misc_continue
import evola.composeapp.generated.resources.misc_duration_min_sec
import evola.composeapp.generated.resources.misc_duration_sec
import evola.composeapp.generated.resources.misc_session_complete
import evola.composeapp.generated.resources.misc_session_subtitle
import evola.composeapp.generated.resources.misc_stat_accuracy
import evola.composeapp.generated.resources.misc_stat_time
import evola.composeapp.generated.resources.misc_stat_words
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.VocabularySessionSummary
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Session completion screen for the Lingvist-style flat SRS queue: confetti icon, headline, 3 stat
 * cards, and a primary action to start the next session or head back to Lesson Details. */
@Composable
fun SessionSummaryScreen(
    summary: VocabularySessionSummary,
    onContinueToNextSession: () -> Unit,
    onDone: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(EvolaSpacing.md))
            Text(stringResource(Res.string.misc_session_complete), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.misc_session_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = EvolaColors.Text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))

            val durationMinutes = (summary.timeSeconds / 60).toInt()
            val durationSeconds = (summary.timeSeconds % 60).toInt()
            val durationText = if (durationMinutes > 0) {
                stringResource(Res.string.misc_duration_min_sec, durationMinutes, durationSeconds)
            } else {
                stringResource(Res.string.misc_duration_sec, durationSeconds)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                StatCard("${summary.wordsLearned}", stringResource(Res.string.misc_stat_words))
                StatCard("${summary.accuracy.toInt()}%", stringResource(Res.string.misc_stat_accuracy))
                StatCard(durationText, stringResource(Res.string.misc_stat_time))
            }
            Spacer(Modifier.height(EvolaSpacing.xl))

            Button(onClick = onContinueToNextSession, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.misc_continue))
            }
            Spacer(Modifier.height(EvolaSpacing.sm))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.misc_back_to_lesson))
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String) {
    Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small) {
        Column(
            modifier = Modifier.padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
        }
    }
}

private val summaryScreenFakeSessionSummary = VocabularySessionSummary(
    sessionNumber = 1, wordsLearned = 8, accuracy = 87.5, timeSeconds = 154, newWordsCount = 5, reviewWordsCount = 3,
)

@Preview
@Composable
private fun SessionSummaryScreenPreview() {
    EvolaTheme {
        SessionSummaryScreen(summary = summaryScreenFakeSessionSummary, onContinueToNextSession = {}, onDone = {})
    }
}
