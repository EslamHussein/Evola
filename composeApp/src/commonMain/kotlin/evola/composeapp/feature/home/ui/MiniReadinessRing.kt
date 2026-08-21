package evola.composeapp.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.profile.domain.AppTheme

/** 40dp single-arc progress ring (vs. the old 80dp 3-segment dial it replaces inside
 * [GoalReadinessCard]) - just track + progress, sized to sit next to the goal text rather than
 * anchor its own row. */
@Composable
internal fun MiniReadinessRing(percent: Int, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val trackColor = EvolaColors.Border
    val progressColor = EvolaColors.Accent
    Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (clamped > 0) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = clamped / 100f * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text("$clamped%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
private fun MiniReadinessRingLightPreview() {
    EvolaTheme(appTheme = AppTheme.LIGHT) {
        MiniReadinessRing(percent = 62)
    }
}

@Preview
@Composable
private fun MiniReadinessRingDarkPreview() {
    EvolaTheme(appTheme = AppTheme.DARK) {
        MiniReadinessRing(percent = 62)
    }
}
