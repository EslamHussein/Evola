package evola.composeapp.onboarding

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.misc_continue
import evola.composeapp.generated.resources.misc_native_language_subtitle
import evola.composeapp.generated.resources.misc_native_language_title
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.SelectableChip
import evola.shared.language.NativeLanguage
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Onboarding step between Welcome and Goal Setup: pick the learner's native language. The
 * selection is carried forward as plain UI state and saved atomically with the goal itself (see
 * `App.kt`) - there's no separate "onboarding progress" persistence to keep in sync. */
@Composable
fun NativeLanguageScreen(onContinue: (NativeLanguage) -> Unit) {
    var selected by remember { mutableStateOf<NativeLanguage?>(null) }

    NativeLanguageContent(
        selected = selected,
        onSelectedChange = { selected = it },
        onContinue = { selected?.let(onContinue) },
    )
}

@Composable
private fun NativeLanguageContent(
    selected: NativeLanguage?,
    onSelectedChange: (NativeLanguage) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(Res.string.misc_native_language_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.misc_native_language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                NativeLanguage.entries.forEach { language ->
                    SelectableChip(
                        label = "${language.englishName} - ${language.nativeName}",
                        selected = selected == language,
                        onClick = { onSelectedChange(language) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.xl))
            Button(
                onClick = onContinue,
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.misc_continue))
            }
        }
    }
}

@Preview
@Composable
private fun NativeLanguageContentPreview() {
    EvolaTheme {
        NativeLanguageContent(selected = NativeLanguage.entries.first(), onSelectedChange = {}, onContinue = {})
    }
}
