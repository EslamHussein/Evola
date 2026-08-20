package evola.composeapp.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_keep_it_up
import evola.composeapp.generated.resources.main_home_learning
import evola.composeapp.generated.resources.main_home_mastered
import evola.composeapp.generated.resources.main_home_needs_practice
import evola.composeapp.generated.resources.main_home_nudge_plural
import evola.composeapp.generated.resources.main_home_nudge_singular
import evola.composeapp.generated.resources.main_home_progress_updates_caption
import evola.composeapp.generated.resources.main_home_review_soon
import evola.composeapp.generated.resources.main_home_well_done
import evola.composeapp.generated.resources.main_home_word_breakdown_title
import evola.composeapp.generated.resources.main_home_words_total
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.onboarding.domain.NudgeWord
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import evola.shared.feature.vocabulary.domain.WordCategory
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** "Word breakdown" header (with the goal's total word count as a pill) plus three red/yellow/
 * green cards - a re-cut of the same words by how they're actually going, not just SRS status:
 * red = the most recent answer was wrong (needs attention now), green = mastered, yellow = touched
 * at least once but still building up. "Not started" (unseen) words don't count as "learning" -
 * they're part of the total pill but don't get their own card, matching the readiness ring's ring
 * (which also treats unseen separately). A word can't land in both red and green - any wrong answer
 * demotes it out of "mastered" immediately (see VocabularySrs.onIncorrect) - so the three cards
 * always sum to less than or equal to the total, the gap being untouched words. */
@Composable
internal fun WordBreakdownSection(vocabulary: VocabularyBreakdown, onStartCategorySession: (WordCategory) -> Unit) {
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    val struggling = vocabulary.struggling
    val mastered = vocabulary.mastered
    val learning = (vocabulary.inProgress - struggling).coerceAtLeast(0)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.main_home_word_breakdown_title), style = MaterialTheme.typography.titleMedium)
        Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.extraLarge) {
            Text(
                stringResource(Res.string.main_home_words_total, total),
                style = MaterialTheme.typography.labelMedium,
                color = EvolaColors.Text2,
                modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.xs),
            )
        }
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        MasteryCard(
            Icons.Filled.TrackChanges, stringResource(Res.string.main_home_needs_practice), stringResource(Res.string.main_home_review_soon), struggling, total,
            EvolaColors.Rust, EvolaColors.RustSoft,
            onClick = { onStartCategorySession(WordCategory.STRUGGLING) }.takeIf { struggling > 0 },
        )
        MasteryCard(
            Icons.AutoMirrored.Filled.MenuBook, stringResource(Res.string.main_home_learning), stringResource(Res.string.main_home_keep_it_up), learning, total,
            EvolaColors.Amber, EvolaColors.AmberSoft,
            onClick = { onStartCategorySession(WordCategory.LEARNING) }.takeIf { learning > 0 },
        )
        MasteryCard(
            Icons.Filled.EmojiEvents, stringResource(Res.string.main_home_mastered), stringResource(Res.string.main_home_well_done), mastered, total,
            EvolaColors.Teal, EvolaColors.TealSoft,
            onClick = { onStartCategorySession(WordCategory.MASTERED) }.takeIf { mastered > 0 },
        )
    }
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(
        stringResource(Res.string.main_home_progress_updates_caption),
        style = MaterialTheme.typography.labelMedium,
        color = EvolaColors.Text3,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** One card of the red/yellow/green breakdown: an icon avatar, title/subtitle, count + share of
 * the total, and a thin progress track. [total] of 0 (no vocabulary yet) renders an empty track
 * rather than dividing by zero. [onClick] is null (and the card non-interactive) when [count] is
 * 0 - nothing to practice in that category yet. */
@Composable
private fun MasteryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    count: Int,
    total: Int,
    color: Color,
    softColor: Color,
    onClick: (() -> Unit)?,
) {
    val fraction = if (total > 0) count / total.toFloat() else 0f
    val percent = if (total > 0) (count * 100f / total).roundToInt() else 0
    Card(
        onClick = onClick ?: {},
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = EvolaColors.SurfaceAlt),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(softColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(EvolaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                Spacer(Modifier.height(EvolaSpacing.sm))
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(EvolaColors.Surface),
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(color),
                    )
                }
            }
            Spacer(Modifier.width(EvolaSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                Text("$count", style = MaterialTheme.typography.titleMedium, color = color)
                Text("|", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Border)
                Text("$percent%", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
            }
        }
    }
}

/** Tappable nudge toward the single word closest to mastered - a concrete, low-effort next step
 * rather than the abstract percentage alone. Routes into the current lesson's vocabulary session,
 * same destination as the main CTA, since the session engine doesn't target a single word. */
@Composable
internal fun NudgeCard(nudge: NudgeWord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(EvolaColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.TrackChanges, contentDescription = null, tint = EvolaColors.Ink2)
            }
            Spacer(Modifier.height(EvolaSpacing.sm))
            val nudgeText = if (nudge.reviewsRemaining == 1) {
                stringResource(Res.string.main_home_nudge_singular, nudge.reviewsRemaining, nudge.term)
            } else {
                stringResource(Res.string.main_home_nudge_plural, nudge.reviewsRemaining, nudge.term)
            }
            Text(
                nudgeText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EvolaColors.Accent)
        }
    }
}

@Preview
@Composable
private fun WordBreakdownSectionPreview() {
    EvolaTheme {
        Column {
            WordBreakdownSection(
                vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
                onStartCategorySession = {},
            )
        }
    }
}

@Preview
@Composable
private fun NudgeCardPreview() {
    EvolaTheme {
        NudgeCard(nudge = NudgeWord(term = "Haus", reviewsRemaining = 2), onClick = {})
    }
}
