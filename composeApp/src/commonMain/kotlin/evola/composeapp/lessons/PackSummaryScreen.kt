package evola.composeapp.lessons

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
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.VocabularyPackSummary

/** Pack completion screen (design handoff Phase 7/8 - "after stage 7 -> 'Finish pack'"): confetti
 * icon, headline, 3 stat cards, and a primary action back to Lesson Details (or straight into the
 * next pack, if there's more vocabulary left for this lesson). */
@Composable
fun PackSummaryScreen(
    summary: VocabularyPackSummary,
    packNumber: Int,
    onContinueToNextPack: () -> Unit,
    onDone: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(EvolaSpacing.md))
            Text("Pack $packNumber complete!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                "Great work - keep it up!",
                style = MaterialTheme.typography.bodyLarge,
                color = EvolaColors.Text2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))

            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                StatCard("${summary.wordsLearned}", "Words")
                StatCard("${summary.accuracy.toInt()}%", "Accuracy")
                StatCard(formatDuration(summary.timeSeconds), "Time")
            }
            Spacer(Modifier.height(EvolaSpacing.xl))

            Button(onClick = onContinueToNextPack, modifier = Modifier.fillMaxWidth()) {
                Text("Continue to Pack ${packNumber + 1}")
            }
            Spacer(Modifier.height(EvolaSpacing.sm))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Lesson")
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

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
}
