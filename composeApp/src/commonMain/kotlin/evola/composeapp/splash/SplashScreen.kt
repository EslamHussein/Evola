package evola.composeapp.splash

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.misc_app_name
import evola.composeapp.generated.resources.misc_splash_tagline
import evola.composeapp.theme.EvolaColors
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

// Timeline cues (seconds), from SPLASH_SCREEN_HANDOFF.md - Build[0,2.2) Beat[2.2,2.6) Reveal[2.6,3.6) Hold[3.6,...).
// The Build phase itself is never replayed here - the OS-level system splash (ic_splash_mark)
// already shows the fully-built mark before this screen's first frame, so this timeline starts
// at CUE_BEAT instead of 0 to pick up exactly where that hand-off left off, rather than resetting
// the mark back to tiny/invisible and re-animating it in a second time.
private const val CUE_BEAT = 2.2f
private const val CUE_REVEAL = 2.6f
private const val CUE_HOLD = 3.6f

// Not in the original (which just loops forever): the splash is data-driven here, not a fixed
// loop - it waits for the real goal-check to finish, but always holds the fully-revealed lockup
// on screen for at least this long first, so a near-instant local DB read never reads as a flicker.
private const val MIN_HOLD_SECONDS = 0.6f
private const val FADE_DUR = 0.5f

private object EaseOutCubic : Easing {
    override fun transform(fraction: Float): Float {
        val f = 1f - fraction
        return 1f - f * f * f
    }
}

private object EaseOutQuart : Easing {
    override fun transform(fraction: Float): Float {
        val f = 1f - fraction
        return 1f - f * f * f * f
    }
}

private object EaseOutBack : Easing {
    override fun transform(fraction: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val f = fraction - 1f
        return 1f + c3 * f * f * f + c1 * f * f
    }
}

private data class SquareSpec(val delay: Float, val size: Dp, val dx: Dp, val dy: Dp, val maxOpacity: Float, val calm: Boolean)

/** The app's icon mark: 3 squares popping in ascending/offset, per SPLASH_SCREEN_HANDOFF.md's exact
 * geometry table (base unit = 42dp - scaled up 1.4x from the original 30dp spec to match the size
 * the OS-level system splash actually renders ic_splash_mark.xml at, measured on-device). Square 3
 * (largest, last) settles calmly (easeOutQuart); 1-2 bounce in (easeOutBack). */
@Composable
private fun Mark(t: Float, breathe: Float, fadeAlpha: Float) {
    val unit = 42.dp
    val squares = remember(unit) {
        listOf(
            SquareSpec(0.00f, unit * 0.7f, unit * -1.625f, unit * 0.25f, 0.45f, calm = false),
            SquareSpec(0.28f, unit * 0.95f, unit * -0.675f, unit * -0.3f, 0.75f, calm = false),
            SquareSpec(0.56f, unit * 1.2f, unit * 0.425f, unit * -0.95f, 1.0f, calm = true),
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = breathe
            scaleY = breathe
            alpha = fadeAlpha
        },
    ) {
        squares.forEach { s ->
            val raw = ((t - s.delay) / 0.55f).coerceIn(0f, 1f)
            val p = if (s.calm) EaseOutQuart.transform(raw) else EaseOutBack.transform(raw)
            val scale = 0.2f + 0.8f * p
            val fade = (p / 0.16f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = s.dx, y = s.dy)
                    .size(s.size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = fade * s.maxOpacity
                    }
                    .background(EvolaColors.Accent, RoundedCornerShape(s.size * 0.16f)),
            )
        }
    }
}

@Composable
private fun Wordmark(t: Float, fadeAlpha: Float) {
    val p = EaseOutCubic.transform(((t - CUE_REVEAL) / 0.6f).coerceIn(0f, 1f))
    val rise = 10.dp * (1f - p)
    Text(
        stringResource(Res.string.misc_app_name),
        modifier = Modifier.offset(y = rise).alpha(p * fadeAlpha),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = 0.36.sp),
        color = EvolaColors.Text,
    )
}

@Composable
private fun Tagline(t: Float, fadeAlpha: Float) {
    val p = EaseOutCubic.transform(((t - CUE_HOLD) / 0.55f).coerceIn(0f, 1f))
    val rise = 8.dp * (1f - p)
    Text(
        stringResource(Res.string.misc_splash_tagline),
        modifier = Modifier.offset(y = rise).alpha(p * fadeAlpha),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.3.sp),
        color = EvolaColors.Text2,
    )
}

/** App launch splash per SPLASH_SCREEN_HANDOFF.md: mark builds, wordmark+tagline reveal, lockup
 * breathes gently while [dataReady] is still false, then fades out and calls [onFinished] once
 * both the animation's minimum reveal time and real data-loading have both completed. */
@Composable
fun SplashScreen(dataReady: Boolean, onFinished: () -> Unit) {
    var t by remember { mutableFloatStateOf(CUE_BEAT) }
    var fading by remember { mutableStateOf(false) }
    var fadeAlpha by remember { mutableFloatStateOf(1f) }
    val currentDataReady by rememberUpdatedState(dataReady)

    LaunchedEffect(Unit) {
        var startNanos = -1L
        var fadeStartElapsed = 0f
        while (true) {
            val nowNanos = withFrameNanos { it }
            if (startNanos < 0) startNanos = nowNanos
            val elapsed = (nowNanos - startNanos) / 1_000_000_000f
            if (!fading) {
                t = elapsed + CUE_BEAT
                if (currentDataReady && t >= CUE_HOLD + MIN_HOLD_SECONDS) {
                    fading = true
                    fadeStartElapsed = elapsed
                }
            } else {
                val fp = ((elapsed - fadeStartElapsed) / FADE_DUR).coerceIn(0f, 1f)
                fadeAlpha = 1f - EaseOutCubic.transform(fp)
                if (fp >= 1f) {
                    onFinished()
                    break
                }
            }
        }
    }

    val breathe = if (t > CUE_HOLD) 1f + 0.03f * sin((t - CUE_HOLD) * 2.4f) else 1f
    val glowBase = if (t < CUE_BEAT) EaseOutCubic.transform((t / CUE_BEAT).coerceIn(0f, 1f)) * 0.16f else 0.16f
    val glowAlpha = glowBase * fadeAlpha

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(EvolaColors.Paper)) {
        // Lockup center at 46% of screen height (per handoff) - 4% above true center.
        val markCenterY = maxHeight * 0.46f
        // The mark itself (not the wordmark/glow) is nudged from that lockup baseline to land
        // exactly where the OS-level system splash renders ic_splash_mark.xml - measured on-device
        // since the vector's viewport-center placement doesn't line up with this composable's
        // naive center otherwise, which showed up as the mark visibly jumping at the handoff.
        val markOffsetX = maxWidth * 0.054f
        val markOffsetY = maxHeight * 0.032f
        val glowSize = 280.dp

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = markOffsetX, y = markCenterY + markOffsetY - glowSize / 2)
                .size(glowSize)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    Brush.radialGradient(colors = listOf(EvolaColors.Accent, Color.Transparent)),
                    shape = CircleShape,
                ),
        )

        Box(modifier = Modifier.align(Alignment.TopCenter).offset(x = markOffsetX, y = markCenterY + markOffsetY)) {
            Mark(t = t, breathe = breathe, fadeAlpha = fadeAlpha)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = markCenterY + markOffsetY + 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(t = t, fadeAlpha = fadeAlpha)
            Tagline(t = t, fadeAlpha = fadeAlpha)
        }
    }
}
