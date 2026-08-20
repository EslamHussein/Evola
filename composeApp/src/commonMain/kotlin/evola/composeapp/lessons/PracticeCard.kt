package evola.composeapp.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_got_it
import evola.composeapp.generated.resources.lessons_action_keep_showing
import evola.composeapp.generated.resources.lessons_action_memorized
import evola.composeapp.generated.resources.lessons_action_missed_it
import evola.composeapp.generated.resources.lessons_content_desc_bookmark
import evola.composeapp.generated.resources.lessons_exercise_choose_options
import evola.composeapp.generated.resources.lessons_exercise_reveal_answer
import evola.composeapp.generated.resources.lessons_exercise_type_answer
import evola.composeapp.generated.resources.lessons_marked_difficult
import evola.composeapp.generated.resources.lessons_mark_difficult
import evola.composeapp.generated.resources.lessons_whats_the_word_for
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Which non-swipe check, if any, is currently active for a [VocabularyCard.Practice] card. Resets
 * every time the card's [VocabularyCard.itemId] changes (a fresh card always starts back on the
 * plain swipe). */
private enum class ExerciseMode { TYPED, CHOICE }

@Composable
internal fun PracticeCard(
    card: VocabularyCard.Practice,
    dueReview: Boolean,
    answered: VocabularyAnswerResult?,
    invertSwipe: Boolean,
    keyboardExerciseEnabled: Boolean,
    multipleChoiceExerciseEnabled: Boolean,
    onSelfGrade: (Boolean) -> Unit,
    onKeepShowing: () -> Unit,
    onSelectChoice: (String) -> Unit,
    onCheckTyped: (String) -> Unit,
    onContinue: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
) {
    var mode by remember(card.itemId) { mutableStateOf<ExerciseMode?>(null) }
    val leftLabel = if (dueReview) stringResource(Res.string.lessons_action_got_it) else stringResource(Res.string.lessons_action_memorized)
    val rightLabel = if (dueReview) stringResource(Res.string.lessons_action_missed_it) else stringResource(Res.string.lessons_action_keep_showing)

    // Bookmark/mark-difficult as visible icon-only toggles, not a "..." overflow menu - matches
    // NewCard's action row, so the same two actions look and behave the same across both card
    // types on this screen instead of one hiding them a tap deeper than the other.
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.lessons_whats_the_word_for), style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            Spacer(Modifier.height(EvolaSpacing.xs))
            RtlText(card.meaning, style = MaterialTheme.typography.headlineMedium)
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
    Spacer(Modifier.height(EvolaSpacing.sm))
    card.grammarNote?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
        Spacer(Modifier.height(EvolaSpacing.sm))
    }
    Spacer(Modifier.height(EvolaSpacing.md))

    when (mode) {
        null -> {
            if (answered == null) {
                SwipeGradeCard(
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    onSwipeLeft = { onSelfGrade(true) },
                    onSwipeRight = { if (dueReview) onSelfGrade(false) else onKeepShowing() },
                    invertSwipe = invertSwipe,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                        if (keyboardExerciseEnabled) {
                            ExerciseIconButton(Icons.Filled.Keyboard, stringResource(Res.string.lessons_exercise_type_answer)) { mode = ExerciseMode.TYPED }
                        }
                        // Reword's "peek" icon - revealing the answer always grades this attempt as
                        // a miss (not knowing it well enough to answer is exactly what asking to see
                        // it first means), reusing the same graded self-grade path a swipe-right on a
                        // due review already uses, rather than a separate ungraded reveal.
                        ExerciseIconButton(Icons.Filled.Visibility, stringResource(Res.string.lessons_exercise_reveal_answer)) { onSelfGrade(false) }
                        if (multipleChoiceExerciseEnabled) {
                            ExerciseIconButton(Icons.Filled.GridView, stringResource(Res.string.lessons_exercise_choose_options)) { mode = ExerciseMode.CHOICE }
                        }
                    }
                }
            } else {
                RevealedInlineSentence(prefix = "", word = answered.correctAnswer ?: "", suffix = "", correct = answered.correct == true)
                Spacer(Modifier.height(EvolaSpacing.sm))
                FeedbackNote(answered)
                AdvanceButton(answered, onContinue)
            }
        }
        ExerciseMode.TYPED -> TypedCheck(card, answered, onCheckTyped, onContinue)
        ExerciseMode.CHOICE -> ChoiceCheck(card, answered, onSelectChoice, onContinue)
    }
}

@Preview
@Composable
private fun PracticeCardPreview() {
    EvolaTheme {
        PracticeCard(
            card = fakePracticeCard,
            dueReview = true,
            answered = null,
            invertSwipe = false,
            keyboardExerciseEnabled = true,
            multipleChoiceExerciseEnabled = true,
            onSelfGrade = {},
            onKeepShowing = {},
            onSelectChoice = {},
            onCheckTyped = {},
            onContinue = {},
            onToggleBookmark = {},
            onToggleDifficult = {},
        )
    }
}
