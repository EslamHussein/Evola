package evola.composeapp.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.GermanNounImportState
import evola.shared.vocabulary.GermanNounImporter

/** Shown only during the one-time German-noun-dataset import ([GermanNounImporter]) - every app
 * launch after the first sees [GermanNounImportState.Done] almost immediately (a single row-count
 * check), so this screen normally never appears at all. A determinate bar, not a spinner, since
 * the real row count is known upfront. */
@Composable
fun VocabDataImportScreen(state: GermanNounImportState) {
    val (imported, total) = when (state) {
        is GermanNounImportState.InProgress -> state.imported to state.total
        else -> 0 to GermanNounImporter.TOTAL_ROWS_HINT
    }
    val progress = if (total > 0) (imported.toFloat() / total).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(EvolaSpacing.xl))
            Text(
                "Setting up German vocabulary data",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            Text(
                "This runs once, so lookups stay fast every time you open the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xxl))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(EvolaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${imported.formatThousands()} / ${total.formatThousands()} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(EvolaSpacing.xxl))
            Text(
                "Data from Wiktionary, licensed under CC BY-SA 4.0.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Int.formatThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
