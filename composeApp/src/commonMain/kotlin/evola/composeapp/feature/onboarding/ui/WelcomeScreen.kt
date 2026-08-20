package evola.composeapp.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.misc_continue
import evola.composeapp.generated.resources.misc_welcome_body
import evola.composeapp.generated.resources.misc_welcome_subtitle
import evola.composeapp.generated.resources.misc_welcome_title
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Onboarding Welcome per 01_PRODUCT_SPEC.md §1.3 - exactly one static screen, no quiz/wizard. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(Res.string.misc_welcome_title), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(EvolaSpacing.lg))
            Text(
                stringResource(Res.string.misc_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.misc_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xxl))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.misc_continue))
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    EvolaTheme {
        WelcomeScreen(onContinue = {})
    }
}
