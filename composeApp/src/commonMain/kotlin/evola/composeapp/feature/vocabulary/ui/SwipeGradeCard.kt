@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** Wraps [content] in a two-direction swipe gesture that fires [onSwipeLeft]/[onSwipeRight] on
 * commit and always snaps back to settled (this screen advances by replacing the card's state, not
 * by animating the row away) - same "swipe reveals/triggers, doesn't remove itself" convention as
 * [evola.composeapp.feature.materials.ui.MaterialsListScreen]'s delete swipe. Below the card, [footer] renders
 * a non-gesture path to the same two actions - it receives the invert-aware label/action pair
 * already resolved, so callers never need to duplicate the invertSwipe logic themselves. The
 * default footer (both actions as equal-weight tappable text) fits [PracticeCard]'s right/wrong
 * grading, where [leftSwipeColor]'s [EvolaColors.Rust] default correctly reads as "incorrect"; a
 * caller like [NewCard], whose left action is a neutral choice rather than a wrong answer, passes
 * its own [footer] and a non-danger [leftSwipeColor]. */
@Composable
internal fun SwipeGradeCard(
    leftLabel: String,
    rightLabel: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    invertSwipe: Boolean = false,
    leftSwipeColor: Color = EvolaColors.Rust,
    rightSwipeColor: Color = EvolaColors.Gold,
    footer: (@Composable (leftLabel: String, rightLabel: String, onLeft: () -> Unit, onRight: () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Settings > Invert swipe direction: swaps which physical side triggers which action, without
    // the caller needing to know or care - the label under a given thumb position always still
    // matches the action it triggers.
    val effectiveLeftLabel = if (invertSwipe) rightLabel else leftLabel
    val effectiveRightLabel = if (invertSwipe) leftLabel else rightLabel
    val effectiveOnSwipeLeft = if (invertSwipe) onSwipeRight else onSwipeLeft
    val effectiveOnSwipeRight = if (invertSwipe) onSwipeLeft else onSwipeRight
    val effectiveLeftColor = if (invertSwipe) rightSwipeColor else leftSwipeColor
    val effectiveRightColor = if (invertSwipe) leftSwipeColor else rightSwipeColor

    val dismissState = rememberSwipeToDismissBoxState()
    // rememberSwipeToDismissBoxState() no longer takes confirmValueChange (deprecated, no direct
    // replacement) - a swipe always commits to its anchor, so the veto-and-snap-back-to-Settled
    // behavior the old callback gave (returning false) is reproduced here explicitly instead.
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> effectiveOnSwipeRight()
            SwipeToDismissBoxValue.EndToStart -> effectiveOnSwipeLeft()
            SwipeToDismissBoxValue.Settled -> return@LaunchedEffect
        }
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val (label, color) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> effectiveRightLabel to effectiveRightColor
                SwipeToDismissBoxValue.EndToStart -> effectiveLeftLabel to effectiveLeftColor
                SwipeToDismissBoxValue.Settled -> "" to Color.Transparent
            }
            Box(modifier = Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
                if (label.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(horizontal = EvolaSpacing.lg))
                }
            }
        },
    ) {
        Surface { content() }
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    if (footer != null) {
        footer(effectiveLeftLabel, effectiveRightLabel, effectiveOnSwipeLeft, effectiveOnSwipeRight)
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                effectiveLeftLabel,
                style = MaterialTheme.typography.labelLarge,
                color = EvolaColors.Rust,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f).clickable(onClick = effectiveOnSwipeLeft),
            )
            Text(
                effectiveRightLabel,
                style = MaterialTheme.typography.labelLarge,
                color = EvolaColors.Gold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).clickable(onClick = effectiveOnSwipeRight),
            )
        }
    }
}

@Preview
@Composable
private fun SwipeGradeCardPreview() {
    EvolaTheme {
        SwipeGradeCard(
            leftLabel = "Got it",
            rightLabel = "Missed it",
            onSwipeLeft = {},
            onSwipeRight = {},
        ) {
            Text("Sample card content", modifier = Modifier.padding(EvolaSpacing.lg))
        }
    }
}
