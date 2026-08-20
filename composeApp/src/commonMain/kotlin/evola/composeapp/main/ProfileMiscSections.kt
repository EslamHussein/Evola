package evola.composeapp.main

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_profile_credits_body
import evola.composeapp.generated.resources.main_profile_credits_title
import evola.composeapp.generated.resources.main_profile_danger_zone_title
import evola.composeapp.generated.resources.main_profile_reset_all_row_subtitle
import evola.composeapp.generated.resources.main_profile_reset_all_row_title
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Reword's Menu "Reset all progress" - a single destructive row, kept separate from [AppSection]
 * so it doesn't sit next to routine actions like Settings/backup. */
@Composable
internal fun DangerZoneSection(onResetAllProgress: () -> Unit) {
    Text(stringResource(Res.string.main_profile_danger_zone_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        AppRow(
            Icons.Filled.Restore,
            stringResource(Res.string.main_profile_reset_all_row_title),
            stringResource(Res.string.main_profile_reset_all_row_subtitle),
            onClick = onResetAllProgress,
        )
    }
}

/** Attribution for bundled third-party data, per its license terms - required regardless of
 * whether the German-noun-lookup feature built on top of it (see
 * [evola.shared.vocabulary.GermanNounLexicon]) is finished yet, since the dataset already ships
 * inside the app binary once bundled as a resource. */
@Composable
internal fun CreditsSection() {
    Text(stringResource(Res.string.main_profile_credits_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(
        stringResource(Res.string.main_profile_credits_body),
        style = MaterialTheme.typography.bodySmall,
        color = EvolaColors.Text3,
    )
}

@Preview
@Composable
private fun DangerZoneSectionPreview() {
    EvolaTheme {
        DangerZoneSection(onResetAllProgress = {})
    }
}

@Preview
@Composable
private fun CreditsSectionPreview() {
    EvolaTheme {
        CreditsSection()
    }
}
