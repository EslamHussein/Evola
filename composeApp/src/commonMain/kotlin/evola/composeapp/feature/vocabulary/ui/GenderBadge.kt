package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

internal fun genderBadgeLabel(gender: String?): String? = when (gender?.lowercase()) {
    "der" -> "m"
    "die" -> "f"
    "das" -> "n"
    else -> null
}

/** Some already-extracted rows have the article baked into [term] itself (a pre-existing extraction
 * slip - see [evola.shared.feature.materials.domain.VocabularyExtractor]); guards this render site so it doesn't show up
 * twice ("Der Der Hund") when we prepend [gender] below. */
internal fun termWithoutDuplicateArticle(term: String, gender: String?): String {
    if (gender.isNullOrBlank()) return term
    val prefix = "$gender "
    return if (term.startsWith(prefix, ignoreCase = true)) term.substring(prefix.length) else term
}

/** der = blue, die = red, das = green - a common learner mnemonic for the article. */
@Composable
internal fun articleColor(gender: String?): Color? = when (gender?.lowercase()) {
    "der" -> EvolaColors.GenderMasculine
    "die" -> EvolaColors.GenderFeminine
    "das" -> EvolaColors.GenderNeuter
    else -> null
}

/** Circle (masculine) / diamond (feminine) / square (neuter) - shape carries the gender, never
 * color alone, per the design's explicit accessibility note. */
@Composable
internal fun GenderBadge(gender: String?) {
    val label = genderBadgeLabel(gender) ?: return
    val shape = when (label) {
        "f" -> RoundedCornerShape(2.dp)
        "n" -> RoundedCornerShape(6.dp)
        else -> CircleShape
    }
    Box(
        modifier = Modifier.size(32.dp).clip(shape).background(EvolaColors.GoldSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Gold)
    }
}

@Preview
@Composable
private fun GenderBadgePreview() {
    EvolaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            GenderBadge(gender = "der")
            GenderBadge(gender = "die")
            GenderBadge(gender = "das")
        }
    }
}
