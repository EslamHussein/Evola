package evola.composeapp.core.designsystem.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SwipeRevealValue { Closed, Revealed }

/**
 * A row that reveals a fixed-width red delete action when swiped left, built on
 * [AnchoredDraggableState] (the same primitive [AppBottomSheetScaffold] uses) instead of
 * [androidx.compose.material3.SwipeToDismissBox]. `SwipeToDismissBox` only has two settled
 * states - "back at rest" or "fully swiped past the far edge" - so a swipe with nowhere further
 * to travel and no partial-drag cancel reads as broken. Here the drag has exactly two anchors,
 * Closed (0) and Revealed (-[revealWidth]), so it stops at a fixed reveal instead of sliding the
 * row off-screen, and releasing before the positional threshold snaps straight back to Closed -
 * that's the cancel gesture, for free, from the same primitive.
 */
@Composable
fun SwipeToRevealDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 72.dp,
    deleteContentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val revealWidthPx = with(density) { revealWidth.toPx() }

    val state = remember {
        AnchoredDraggableState(
            initialValue = SwipeRevealValue.Closed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = tween(300),
            decayAnimationSpec = splineBasedDecay(density),
        )
    }
    val anchors = remember(revealWidthPx) {
        DraggableAnchors {
            SwipeRevealValue.Closed at 0f
            SwipeRevealValue.Revealed at -revealWidthPx
        }
    }
    LaunchedEffect(anchors) { state.updateAnchors(anchors) }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.medium).background(EvolaColors.Rust),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(
                onClick = {
                    onDelete()
                    scope.launch { state.animateTo(SwipeRevealValue.Closed) }
                },
                modifier = Modifier.padding(horizontal = EvolaSpacing.lg),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = deleteContentDescription, tint = Color.White)
            }
        }
        Box(
            modifier = Modifier
                .offset {
                    // .offset() throws until updateAnchors() runs (first LaunchedEffect pass) -
                    // fall back to Closed's position (0) until then.
                    val offset = state.offset
                    IntOffset((if (offset.isNaN()) 0f else offset).roundToInt(), 0)
                }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
        }
    }
}
