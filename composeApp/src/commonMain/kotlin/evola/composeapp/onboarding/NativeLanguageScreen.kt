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
import androidx.compose.ui.unit.dp
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.SelectableChip
import evola.shared.language.NativeLanguage

/** Onboarding step between Welcome and Goal Setup: pick the learner's native language. The
 * selection is carried forward as plain UI state and saved atomically with the goal itself (see
 * `App.kt`) - there's no separate "onboarding progress" persistence to keep in sync. */
@Composable
fun NativeLanguageScreen(onContinue: (NativeLanguage) -> Unit) {
    var selected by remember { mutableStateOf<NativeLanguage?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("What's your native language?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "We'll translate new words into this language for you.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                NativeLanguage.entries.forEach { language ->
                    SelectableChip(
                        label = "${language.englishName} - ${language.nativeName}",
                        selected = selected == language,
                        onClick = { selected = language },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { selected?.let(onContinue) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}
