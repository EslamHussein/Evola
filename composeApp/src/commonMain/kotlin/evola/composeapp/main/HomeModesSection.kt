package evola.composeapp.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_browse_flashcards
import evola.composeapp.generated.resources.main_home_extra_modes_subtitle
import evola.composeapp.generated.resources.main_home_extra_modes_title
import evola.composeapp.generated.resources.main_home_hands_free_mode
import evola.composeapp.generated.resources.main_home_learn_new_words
import evola.composeapp.generated.resources.main_home_learned_today
import evola.composeapp.generated.resources.main_home_mixed_mode
import evola.composeapp.generated.resources.main_home_mixed_mode_subtitle
import evola.composeapp.generated.resources.main_home_review_words
import evola.composeapp.generated.resources.main_home_study_section_title
import evola.composeapp.generated.resources.main_home_words_to_review
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.goals.GoalProgress
import evola.shared.vocabulary.SessionMode
import org.jetbrains.compose.resources.stringResource

/** Reword's Home "Spaced repetition" section - three rows with real counts, each a plain multi-word
 * session across the whole goal (see [evola.shared.vocabulary.SessionMode]) rather than tied to any
 * one lesson. Mirrors Reword's structure - own wording, own icon language. A row with nothing
 * available (0 due, or 0 new against today's daily-goal remainder) is still shown, just non-tappable,
 * same "always visible, disabled when empty" convention [evola.composeapp.main.MasteryCard] already uses. */
@Composable
internal fun SessionModesSection(progress: GoalProgress, onStartModeSession: (SessionMode) -> Unit) {
    val newRemaining = (progress.dailyGoal - progress.todayNewWordsLearned).coerceAtLeast(0)
    Text(stringResource(Res.string.main_home_study_section_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            ModeRow(
                Icons.Filled.Add, EvolaColors.Rust, stringResource(Res.string.main_home_learn_new_words),
                stringResource(Res.string.main_home_learned_today, progress.todayNewWordsLearned, progress.dailyGoal),
                onClick = { onStartModeSession(SessionMode.NEW_ONLY) }.takeIf { newRemaining > 0 },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            ModeRow(
                Icons.Filled.History, EvolaColors.Amber, stringResource(Res.string.main_home_review_words),
                stringResource(Res.string.main_home_words_to_review, progress.wordsToReviewCount),
                onClick = { onStartModeSession(SessionMode.REVIEW_ONLY) }.takeIf { progress.wordsToReviewCount > 0 },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            ModeRow(
                Icons.Filled.Lightbulb, EvolaColors.Accent, stringResource(Res.string.main_home_mixed_mode),
                stringResource(Res.string.main_home_mixed_mode_subtitle),
                onClick = { onStartModeSession(SessionMode.MIXED) }.takeIf { newRemaining > 0 || progress.wordsToReviewCount > 0 },
            )
        }
    }
}

/** Reword's row icon treatment - a small outlined circle around the icon, tinted per-row (not just
 * a plain bare icon), confirmed against a live screenshot. [badgeColor] is the row's own identity
 * color, dimmed to [EvolaColors.Text3] on both the ring and the icon when the row is disabled -
 * same disabled convention [ModeRow]'s text already used. */
@Composable
private fun ModeRow(icon: ImageVector, badgeColor: Color, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (onClick != null) badgeColor else EvolaColors.Text3
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (onClick != null) EvolaColors.Text else EvolaColors.Text3)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        }
    }
}

/** Reword's "Extra modes (do not affect stats)" section - Browse flashcards / Hands-free, both
 * already-existing features surfaced here as their own Home rows instead of buried under the
 * "Continue Lesson" CTA, matching Reword's placement. Scoped to the current lesson same as the CTA
 * below it, since neither feature has a goal-wide entry point today. */
@Composable
internal fun ExtraModesSection(onBrowseFlashcards: () -> Unit, onStartHandsFree: () -> Unit) {
    Text(stringResource(Res.string.main_home_extra_modes_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(2.dp))
    Text(stringResource(Res.string.main_home_extra_modes_subtitle), style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
    Spacer(Modifier.height(EvolaSpacing.md))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBrowseFlashcards).padding(EvolaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text(stringResource(Res.string.main_home_browse_flashcards), style = MaterialTheme.typography.titleSmall)
            }
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onStartHandsFree).padding(EvolaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text(stringResource(Res.string.main_home_hands_free_mode), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
