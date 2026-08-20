package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Static version of the splash/loading indicator's 3-square mark, for root-tab top bars.
 * Same 72x60 coordinate space and squares as [evola.composeapp.core.common.ChaseLoadingIndicator],
 * just fully opaque and non-animated - a small brand anchor rather than a loading cue. */
@Composable
fun AppLogoMark(
    modifier: Modifier = Modifier,
    color: Color = EvolaColors.Accent,
    size: Dp = 28.dp,
) {
    val height = size * (60f / 72f)
    Canvas(modifier = modifier.size(size, height)) {
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
        square(4f, 34f, 16f, 16f, 2.5f, 0.55f)
        square(24f, 20f, 20f, 20f, 3f, 0.8f)
        square(46f, 4f, 24f, 24f, 3.5f, 1f)
    }
}

/** Root-tab top bar title: [AppLogoMark] + text. Reserved for the 4 tab-root screens (Home,
 * Study, Materials, Profile) - sub-screens reached by navigating within a tab use a plain title
 * so the mark reads as "you're at the top level of a tab", not decoration on every screen. */
@Composable
fun RootTopBarTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
        AppLogoMark()
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Preview
@Composable
private fun RootTopBarTitlePreview() {
    EvolaTheme {
        RootTopBarTitle(text = "Evola")
    }
}
