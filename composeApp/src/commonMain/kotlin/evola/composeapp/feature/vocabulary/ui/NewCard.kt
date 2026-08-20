package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_already_know
import evola.composeapp.generated.resources.lessons_action_play_pronunciation
import evola.composeapp.generated.resources.lessons_action_start_learning
import evola.composeapp.generated.resources.lessons_ai_explain
import evola.composeapp.generated.resources.lessons_content_desc_bookmark
import evola.composeapp.generated.resources.lessons_marked_difficult
import evola.composeapp.generated.resources.lessons_mark_difficult
import evola.composeapp.generated.resources.lessons_new_word_label
import evola.composeapp.core.common.LocalNativeLanguage
import evola.composeapp.core.common.RtlText
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.VocabularyCard
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun NewCard(
    card: VocabularyCard.New,
    explainLoading: Boolean,
    invertSwipe: Boolean,
    showTranscription: Boolean,
    onAlreadyKnown: () -> Unit,
    onStartLearning: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
    onExplain: () -> Unit,
    onPlayPronunciation: () -> Unit,
) {
    val alreadyKnowLabel = stringResource(Res.string.lessons_action_already_know)
    val startLearningLabel = stringResource(Res.string.lessons_action_start_learning)
    SwipeGradeCard(
        leftLabel = alreadyKnowLabel,
        rightLabel = startLearningLabel,
        onSwipeLeft = onAlreadyKnown,
        onSwipeRight = onStartLearning,
        invertSwipe = invertSwipe,
        // "I already know this" is a neutral, non-destructive choice (unlike PracticeCard's
        // right/wrong grading swipe, where the default Rust legitimately means "incorrect") - Ink2
        // reads as a real state change without reading as an error.
        leftSwipeColor = EvolaColors.Ink2,
        footer = { leftLabel, rightLabel, onLeft, onRight ->
            Column(modifier = Modifier.fillMaxWidth()) {
                // The one accent-filled button on the card - "Start learning this word" is the
                // primary path forward, so it's the only control with that visual weight.
                Button(onClick = onRight, modifier = Modifier.fillMaxWidth()) {
                    Text(rightLabel)
                }
                Spacer(Modifier.height(EvolaSpacing.sm))
                // Plain text, neutral color - previously Rust (the danger/error role) on an action
                // that isn't destructive at all, just a different, equally valid outcome.
                TextButton(
                    onClick = onLeft,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = EvolaColors.Text2),
                ) {
                    Text(leftLabel)
                }
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // "New word" badge - pill shape, accent-tinted background (previously a bare icon+text
            // row with no container, easy to mistake for body copy rather than a status marker).
            Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.AccentSoft) {
                Row(
                    modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(EvolaSpacing.xs))
                    Text(stringResource(Res.string.lessons_new_word_label), style = MaterialTheme.typography.labelMedium, color = EvolaColors.Accent)
                }
            }
            Spacer(Modifier.height(EvolaSpacing.lg))

            // The word - the card's visual anchor, centered and large. The bookmark toggle that
            // previously sat in this row now lives in the action row below with the other
            // icon-only toggle, so nothing competes with the word for attention here.
            Row(verticalAlignment = Alignment.CenterVertically) {
                GenderBadge(card.gender)
                if (genderBadgeLabel(card.gender) != null) Spacer(Modifier.width(EvolaSpacing.sm))
                Text(
                    buildAnnotatedString {
                        card.gender?.let { gender ->
                            articleColor(gender)?.let { color ->
                                withStyle(SpanStyle(color = color)) { append("$gender ") }
                            } ?: append("$gender ")
                        }
                        append(termWithoutDuplicateArticle(card.term, card.gender))
                    },
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
            }

            val posLine = listOfNotNull(card.partOfSpeech, card.plural?.let { "Pl. $it" }).joinToString(" · ")
            if (posLine.isNotEmpty()) {
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(posLine, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            }
            Spacer(Modifier.height(EvolaSpacing.lg))

            // Pronunciation - a real circular button (filled-tonal, not a bare icon) so it reads as
            // its own tappable control rather than decoration next to the transcription.
            FilledTonalIconButton(onClick = onPlayPronunciation, shape = CircleShape) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(Res.string.lessons_action_play_pronunciation), tint = EvolaColors.Accent)
            }
            if (showTranscription) {
                card.ipaPronunciation?.let {
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                }
            }
            Spacer(Modifier.height(EvolaSpacing.lg))

            HorizontalDivider(color = EvolaColors.Border)
            Spacer(Modifier.height(EvolaSpacing.lg))

            Text(
                LocalNativeLanguage.current.code.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = EvolaColors.Text3,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            RtlText(card.meaning, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(EvolaSpacing.lg))

            card.exampleSentence?.let { sentence ->
                Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(EvolaSpacing.md)) {
                        Text(sentence, style = MaterialTheme.typography.bodyMedium)
                        card.exampleSentenceTranslation?.let {
                            Spacer(Modifier.height(EvolaSpacing.xs))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                        }
                    }
                }
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            card.grammarNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            if (card.relatedWords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                    card.relatedWords.forEach { related ->
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
                            Text(related, modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = EvolaSpacing.xs), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                card.difficultyRating?.let {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.GoldSoft) {
                        Row(modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = EvolaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(EvolaSpacing.xs))
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                card.frequencyRating?.let {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.Surface, border = BorderStroke(1.dp, EvolaColors.Border)) {
                        Text(it, modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = EvolaSpacing.xs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))

            card.memoryTip?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            // Action row - AI explain (exploratory/optional) takes the remaining width; mark-difficult
            // and save are icon-only toggles of equal, lesser visual weight, matching each other.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                val aiExplanation = card.aiExplanation
                if (aiExplanation == null) {
                    OutlinedButton(onClick = onExplain, enabled = !explainLoading, modifier = Modifier.weight(1f)) {
                        if (explainLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EvolaColors.Accent)
                            Spacer(Modifier.width(EvolaSpacing.sm))
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(EvolaSpacing.xs))
                        }
                        Text(stringResource(Res.string.lessons_ai_explain))
                    }
                } else {
                    Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.padding(EvolaSpacing.md)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(EvolaSpacing.sm))
                            Text(aiExplanation, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                val markDifficultDesc = if (card.markedDifficult) stringResource(Res.string.lessons_marked_difficult) else stringResource(Res.string.lessons_mark_difficult)
                FilledTonalIconToggleButton(checked = card.markedDifficult, onCheckedChange = { onToggleDifficult() }) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = markDifficultDesc,
                        tint = if (card.markedDifficult) EvolaColors.Rust else EvolaColors.Text3,
                    )
                }
                FilledTonalIconToggleButton(checked = card.isBookmarked, onCheckedChange = { onToggleBookmark() }) {
                    Icon(
                        if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(Res.string.lessons_content_desc_bookmark),
                        tint = if (card.isBookmarked) EvolaColors.Gold else EvolaColors.Text3,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun NewCardPreview() {
    EvolaTheme {
        NewCard(
            card = fakeNewCard,
            explainLoading = false,
            invertSwipe = false,
            showTranscription = true,
            onAlreadyKnown = {},
            onStartLearning = {},
            onToggleBookmark = {},
            onToggleDifficult = {},
            onExplain = {},
            onPlayPronunciation = {},
        )
    }
}
