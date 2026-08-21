package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** Small-caps-weight label heading over a settings/profile/detail section - `labelLarge` in
 * [EvolaColors.tertiary]-equivalent [MaterialTheme.colorScheme.tertiary] (Reword's periwinkle
 * indigo), marked as a real accessibility heading via [heading] rather than styled text alone. */
@Composable
fun EvolaSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.semantics { heading() },
    )
}

@Preview
@Composable
private fun EvolaSectionHeaderPreview() {
    EvolaTheme {
        Column(modifier = Modifier.background(EvolaColors.Paper).padding(EvolaSpacing.lg)) {
            EvolaSectionHeader(text = "Account")
            EvolaSectionHeader(text = "Danger zone", modifier = Modifier.padding(top = EvolaSpacing.md))
        }
    }
}
