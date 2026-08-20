package evola.composeapp.core.designsystem.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppBottomSheetValue { Hidden, Collapsed, HalfExpanded, Expanded }

private const val HALF_EXPANDED_FRACTION = 0.5f
private const val EXPANDED_FRACTION = 0.92f

/**
 * Externally-controllable state for [AppBottomSheetScaffold], backed by
 * [androidx.compose.foundation.gestures.AnchoredDraggableState] (commonMain, no platform-specific
 * code) instead of Material3's modal-only `SheetState` - gives full control over the four values
 * and lets the sheet coexist with a persistent bottom bar rather than covering it.
 */
@Stable
class AppBottomSheetState internal constructor(
    initialValue: AppBottomSheetValue,
) {
    internal val anchoredDraggableState = AnchoredDraggableState(initialValue = initialValue)

    val currentValue: AppBottomSheetValue get() = anchoredDraggableState.currentValue
    val targetValue: AppBottomSheetValue get() = anchoredDraggableState.targetValue

    suspend fun hide() = anchoredDraggableState.animateTo(AppBottomSheetValue.Hidden)
    suspend fun collapse() = anchoredDraggableState.animateTo(AppBottomSheetValue.Collapsed)
    suspend fun halfExpand() = anchoredDraggableState.animateTo(AppBottomSheetValue.HalfExpanded)
    suspend fun expand() = anchoredDraggableState.animateTo(AppBottomSheetValue.Expanded)

    /** Shorthand for "make it visible at its smallest useful size" - Collapsed, not Hidden. */
    suspend fun show() = anchoredDraggableState.animateTo(AppBottomSheetValue.Collapsed)
}

@Composable
fun rememberAppBottomSheetState(
    initialValue: AppBottomSheetValue = AppBottomSheetValue.Hidden,
): AppBottomSheetState = remember { AppBottomSheetState(initialValue) }

/**
 * A persistent, non-modal bottom sheet that never covers [bottomBar] - unlike
 * `ModalBottomSheet`/`BottomSheetScaffold` (which own the whole bottom edge themselves, including
 * wherever a separate bottom navigation bar would sit), this scaffold puts [bottomBar] as its own
 * fixed-height sibling in an outer `Column`, and confines the sheet + [content] to the
 * `weight(1f)` region above it. That's a layout guarantee, not a z-order trick: the sheet
 * physically cannot render past the top edge of [bottomBar]'s region, at any drag position or
 * state, on any screen size - it's bounded by [BoxWithConstraints]' measured `maxHeight` for that
 * region, which [bottomBar]'s own height has already been subtracted from by the `Column`.
 *
 * States: [AppBottomSheetValue.Hidden] (off-screen below), [AppBottomSheetValue.Collapsed] (only
 * [peekHeight] visible), [AppBottomSheetValue.HalfExpanded] (~50% of the region above bottomBar),
 * [AppBottomSheetValue.Expanded] (~92%, leaving a small gap so it never feels like a full takeover
 * of what's already a bounded area). Drag between states naturally; snapping/thresholds are
 * [AppBottomSheetState]'s [AnchoredDraggableState] doing what it already does for any anchored
 * drag surface (same primitive `ModalBottomSheet` itself is built on internally).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheetScaffold(
    sheetState: AppBottomSheetState,
    bottomBar: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 64.dp,
    showDragHandle: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize()) { content() }

            val containerHeightPx = with(density) { maxHeight.toPx() }
            if (containerHeightPx > 0f) {
                val peekHeightPx = with(density) { peekHeight.toPx() }
                val halfHeightPx = containerHeightPx * HALF_EXPANDED_FRACTION
                val expandedHeightPx = containerHeightPx * EXPANDED_FRACTION

                val anchors = remember(containerHeightPx, peekHeightPx) {
                    DraggableAnchors {
                        AppBottomSheetValue.Hidden at containerHeightPx
                        AppBottomSheetValue.Collapsed at (containerHeightPx - peekHeightPx)
                        AppBottomSheetValue.HalfExpanded at (containerHeightPx - halfHeightPx)
                        AppBottomSheetValue.Expanded at (containerHeightPx - expandedHeightPx)
                    }
                }
                LaunchedEffect(anchors) { sheetState.anchoredDraggableState.updateAnchors(anchors) }
                val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state = sheetState.anchoredDraggableState,
                    animationSpec = tween(300),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(maxHeight)
                        .offset {
                            // requireOffset() throws until updateAnchors() has run at least once -
                            // that happens in a LaunchedEffect, which fires after this first layout
                            // pass. Fall back to fully off-screen (Hidden's position) until then.
                            val offset = sheetState.anchoredDraggableState.offset
                            IntOffset(0, (if (offset.isNaN()) containerHeightPx else offset).roundToInt())
                        }
                        .anchoredDraggable(sheetState.anchoredDraggableState, Orientation.Vertical, flingBehavior = flingBehavior),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 4.dp,
                ) {
                    Column {
                        if (showDragHandle) {
                            // A tap on just the handle (not the whole sheet, which would fight
                            // the drag gesture below it) expands from Collapsed - "tapping the
                            // collapsed sheet expands it".
                            val handleModifier = if (sheetState.currentValue == AppBottomSheetValue.Collapsed) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = "Expand",
                                ) { scope.launch { sheetState.expand() } }
                            } else {
                                Modifier
                            }
                            Box(
                                // heightIn(min = 48.dp), not padding - the visual handle stays a
                                // thin bar, but the tap target it sits in meets the M3 minimum.
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(handleModifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                BottomSheetDefaults.DragHandle()
                            }
                        }
                        sheetContent()
                    }
                }
            }
        }
        bottomBar()
    }
}
