package evola.composeapp.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_exam_readiness
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.EvolaCard
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Colors for the mini readiness ring's mastered/learning/not-started segments. */
private val MasteredColor: Color @Composable get() = EvolaColors.Accent
private val LearningColor: Color @Composable get() = EvolaColors.Ink2
private val NotStartedColor: Color @Composable get() = EvolaColors.Text3

/** Evola-original addition, no Reword equivalent - kept (per this dashboard's own earlier design
 * note: "a stronger design than Reword's own streak-only view") but no longer paired with a
 * duplicate streak tile now that [StatsSection] owns all streak content on its own, matching
 * Reword's structure. */
@Composable
internal fun TopTilesRow(percent: Int, vocabulary: VocabularyBreakdown) {
    EvolaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessRing(percent = percent, vocabulary = vocabulary)
            Spacer(Modifier.width(EvolaSpacing.md))
            Text(stringResource(Res.string.main_home_exam_readiness), style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text2)
        }
    }
}

/** Compact readiness dial (80dp): a 3-segment ring - mastered, then learning, remainder unfilled -
 * so the ring itself shows what the % is made of, with the number centered inside it. */
@Composable
private fun ReadinessRing(percent: Int, vocabulary: VocabularyBreakdown, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    val masteredColor = MasteredColor
    val learningColor = LearningColor
    val notStartedColor = NotStartedColor
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            fun arc(color: Color, startAngle: Float, sweepAngle: Float) {
                if (sweepAngle <= 0f) return
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            arc(notStartedColor, -90f, 360f)
            if (total > 0) {
                val masteredSweep = vocabulary.mastered / total.toFloat() * 360f
                val learningSweep = vocabulary.inProgress / total.toFloat() * 360f
                arc(masteredColor, -90f, masteredSweep)
                arc(learningColor, -90f + masteredSweep, learningSweep)
            }
        }
        Text("$clamped%", style = MaterialTheme.typography.titleLarge)
    }
}

private val fakeReadinessVocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3)

@Preview
@Composable
private fun TopTilesRowPreview() {
    EvolaTheme {
        TopTilesRow(percent = 62, vocabulary = fakeReadinessVocabulary)
    }
}
