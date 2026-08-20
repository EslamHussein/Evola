package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_tutorial_know_it
import evola.composeapp.generated.resources.lessons_tutorial_missed_it
import evola.composeapp.generated.resources.lessons_tutorial_start_learning
import evola.composeapp.generated.resources.lessons_tutorial_swipe_hint
import evola.composeapp.generated.resources.lessons_tutorial_tap_hint
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Shown once, ever, the first time a learner reaches an in-progress card - Reword's own onboarding
 * has an equivalent swipe walkthrough; this app's content is lesson-scoped rather than pre-loaded
 * decks so there's no separate category-picker step to hang it off, but the gesture itself is
 * identical and just as non-obvious on first use. Dismissed by tapping anywhere, which flips
 * [evola.shared.feature.profile.data.LocalSettingsRepository.setHasSeenSwipeTutorial] so it never shows again. */
@Composable
internal fun SwipeTutorialOverlay(invertSwipe: Boolean, onDismiss: () -> Unit) {
    val missedItNotYetLabel = stringResource(Res.string.lessons_tutorial_missed_it)
    val iKnowItLabel = stringResource(Res.string.lessons_tutorial_know_it)
    val startLearningShortLabel = stringResource(Res.string.lessons_tutorial_start_learning)
    val knowItLabel = if (invertSwipe) missedItNotYetLabel else iKnowItLabel
    val learnItLabel = if (invertSwipe) iKnowItLabel else startLearningShortLabel
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xCC1C1E27))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(EvolaSpacing.xl),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = EvolaColors.Rust,
                modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = 90f },
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(knowItLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(EvolaSpacing.xl))
            Text(
                stringResource(Res.string.lessons_tutorial_swipe_hint),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            Text(
                stringResource(Res.string.lessons_tutorial_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = EvolaColors.Gold,
                modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = 270f },
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(learnItLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Preview
@Composable
private fun SwipeTutorialOverlayPreview() {
    EvolaTheme {
        SwipeTutorialOverlay(invertSwipe = false, onDismiss = {})
    }
}
