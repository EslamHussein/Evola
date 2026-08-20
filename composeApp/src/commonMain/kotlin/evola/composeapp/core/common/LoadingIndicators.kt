package evola.composeapp.core.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import evola.composeapp.core.common.isReduceMotionEnabled
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import androidx.compose.ui.tooling.preview.Preview

/** Per LOADING_INDICATORS_HANDOFF.md: a single traveling brightness wave over 3 phase-offset
 * squares/dots, cycle length 1.2s. [cyclePos] and [offset] are both in [0,1). */
private fun chase(cyclePos: Float, offset: Float): Float {
    val phase = ((cyclePos - offset + 1f) % 1f) - 0.1f
    val raw = 0.15f + 0.85f * max(0f, cos(phase * PI.toFloat() * 1.8f))
    return raw.coerceIn(0f, 1f)
}

/** Drives the shared 1.2s chase cycle, or a fixed reduced-motion fallback. Returns the 3 phase
 * opacities (offsets 0, 0.22, 0.44 per the handoff). */
@Composable
private fun rememberChaseOpacities(): FloatArray {
    val reduceMotion = isReduceMotionEnabled()
    if (reduceMotion) {
        // Static, non-animated dim/bright state per the handoff's accessibility note.
        return remember { floatArrayOf(1f, 0.6f, 0.3f) }
    }
    val transition = rememberInfiniteTransition(label = "chase")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "cycle",
    )
    return floatArrayOf(chase(cycle, 0f), chase(cycle, 0.22f), chase(cycle, 0.44f))
}

/** Full-size "chase" loading indicator: the app mark's 3 ascending squares, pulsing in a
 * traveling sequence instead of a generic spinner. For page loads / sync states. [color] defaults
 * to the ambient content color like the rest of the design system's currentColor convention. */
@Composable
fun ChaseLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 72.dp,
) {
    val opacities = rememberChaseOpacities()
    val height = size * (60f / 72f)
    Canvas(modifier = modifier.width(size).height(height)) {
        val scale = this.size.width / 72f
        fun square(x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(r * scale, r * scale),
                alpha = alpha,
            )
        }
        square(4f, 34f, 16f, 16f, 2.5f, opacities[0])
        square(24f, 20f, 20f, 20f, 3f, opacities[1])
        square(46f, 4f, 24f, 24f, 3.5f, opacities[2])
    }
}

/** Tiny-dot loading indicator for inline/button spinner contexts (16-20px, e.g. a secondary
 * button showing "Saving..."). Same ascending arrangement and chase timing as
 * [ChaseLoadingIndicator], simplified to dots since square detail doesn't read at this size.
 * [color] defaults to the ambient content color so it matches whatever text it sits inside. */
@Composable
fun ChaseLoadingDots(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    val opacities = rememberChaseOpacities()
    // Authored coordinate space is ~28 wide x 20 tall; scale keyed off the 20-unit height so
    // `size` reads as "the dot indicator's height", matching how it'll usually be sized inline
    // next to text.
    val width = size * (28f / 20f)
    Canvas(modifier = modifier.width(width).height(size)) {
        val scale = this.size.height / 20f
        fun dot(cx: Float, cy: Float, r: Float, alpha: Float) {
            drawCircle(color = color, radius = r * scale, center = Offset(cx * scale, cy * scale), alpha = alpha)
        }
        dot(6f, 16f, 3f, opacities[0])
        dot(14f, 10f, 3.6f, opacities[1])
        dot(23f, 4f, 4.2f, opacities[2])
    }
}

@Preview
@Composable
private fun ChaseLoadingIndicatorPreview() {
    EvolaTheme {
        Column(
            modifier = Modifier.background(EvolaColors.Paper).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ChaseLoadingIndicator(color = EvolaColors.Accent)
        }
    }
}

@Preview
@Composable
private fun ChaseLoadingDotsPreview() {
    EvolaTheme {
        Column(
            modifier = Modifier.background(EvolaColors.Paper).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ChaseLoadingDots(color = EvolaColors.Accent)
        }
    }
}
