package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.CenteredMessage
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.misc_retry
import org.jetbrains.compose.resources.stringResource

/** The app's one "something went wrong" state - built on [CenteredMessage] so every screen's error
 * branch converges on one centered layout instead of each hand-rolling its own `Box(fillMaxSize,
 * Center)`. [onRetry] is optional since some error states (e.g. a topic list with no retry hook)
 * have nothing to retry; when passed, the retry button always reads "Retry" ([misc_retry]) - one
 * consistent label rather than each call site's own copy. */
@Composable
fun EvolaErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    CenteredMessage {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(EvolaSpacing.lg))
                EvolaSecondaryButton(text = stringResource(Res.string.misc_retry), onClick = onRetry)
            }
        }
    }
}

/** The app's one "nothing here yet" state - built on [CenteredMessage], same reasoning as
 * [EvolaErrorState]. [icon], when passed, sits above the message at a fixed size/tint so a caller
 * doesn't need to pick its own icon treatment. */
@Composable
fun EvolaEmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    CenteredMessage {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = EvolaColors.Text3, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(EvolaSpacing.md))
            }
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = EvolaColors.Text2)
        }
    }
}

@Preview
@Composable
private fun EvolaEmptyStatePreview() {
    EvolaTheme {
        Column(modifier = Modifier.background(EvolaColors.Paper)) {
            EvolaErrorState(message = "Couldn't load your lessons.", onRetry = {})
            EvolaEmptyState(message = "No words yet.", icon = Icons.Filled.Inbox)
        }
    }
}
