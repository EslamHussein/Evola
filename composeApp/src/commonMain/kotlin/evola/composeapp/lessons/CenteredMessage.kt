package evola.composeapp.lessons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Preview
@Composable
private fun CenteredMessagePreview() {
    EvolaTheme {
        CenteredMessage { Text("Sample message") }
    }
}
