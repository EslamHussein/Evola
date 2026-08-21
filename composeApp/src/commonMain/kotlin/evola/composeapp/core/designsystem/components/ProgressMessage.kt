package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** Full-screen spinner + message, for a loading state with real work happening in the background
 * (lesson segmentation, upload processing) rather than just a static wait. Used by both `learning`
 * and `materials` - promoted here once a second feature needed it, matching this app's existing
 * core-promotion convention. */
@Composable
fun ProgressMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChaseLoadingIndicator()
            Spacer(Modifier.height(EvolaSpacing.lg))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
private fun ProgressMessagePreview() {
    EvolaTheme {
        ProgressMessage("Analyzing your material…")
    }
}
