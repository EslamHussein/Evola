package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import kotlin.math.min
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A selectable, icon+label chip (resource-type grid, wizard single/multi-select options) -
 * recreates the design handoff's outlined/tinted-when-selected chip using EvolaTheme colors
 * instead of Nocturne's dark palette. */
@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val borderColor = if (selected) EvolaColors.Gold else EvolaColors.Border
    val bgColor = if (selected) EvolaColors.GoldSoft else EvolaColors.Surface
    val contentColor = when {
        !enabled -> EvolaColors.Text3
        selected -> EvolaColors.Ink
        else -> EvolaColors.Text
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        border = BorderStroke(1.dp, if (enabled) borderColor else EvolaColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(EvolaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = contentColor)
            Text(label, color = contentColor, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 36px rounded-square icon tile used in Lesson Details section rows - tinted when unlocked,
 * dimmed/neutral when locked. */
@Composable
fun IconTile(icon: ImageVector, locked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (locked) EvolaColors.SurfaceAlt else EvolaColors.GoldSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (locked) EvolaColors.Text3 else EvolaColors.Gold)
    }
}

enum class StatusTagStyle { FILLED, OUTLINE, NEUTRAL }

/** Three-state pill tag - FILLED for "done/ready" (Fertig-equivalent), OUTLINE for "in progress"
 * (Läuft-equivalent), NEUTRAL for "not started" (Offen-equivalent). Gold is reserved for
 * progress/readiness per the existing EvolaColors convention, so FILLED uses it. */
@Composable
fun StatusTag(label: String, style: StatusTagStyle, modifier: Modifier = Modifier) {
    val (bg, border, text) = when (style) {
        StatusTagStyle.FILLED -> Triple(EvolaColors.GoldSoft, null, EvolaColors.Ink)
        StatusTagStyle.OUTLINE -> Triple(EvolaColors.Surface, EvolaColors.Teal, EvolaColors.Teal)
        StatusTagStyle.NEUTRAL -> Triple(EvolaColors.SurfaceAlt, null, EvolaColors.Text3)
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = bg,
        border = border?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 2.dp),
            color = text,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** N-segment header progress bar for the AI Wizard's 4 steps - filled segments in Gold, unfilled
 * in a low-opacity neutral tone. */
@Composable
fun SegmentedProgressBar(segmentCount: Int, filledCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
        repeat(segmentCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filledCount) EvolaColors.Gold else EvolaColors.Border),
            )
        }
    }
}

/** Conic-gradient percentage ring (52px Resource Details / 40px Lesson Details). */
@Composable
fun CircularProgressRing(percent: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 52.dp) {
    val clamped = min(100, percent).coerceAtLeast(0)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        0f to EvolaColors.Gold,
                        (clamped / 100f).coerceIn(0.001f, 0.999f) to EvolaColors.Gold,
                        (clamped / 100f).coerceIn(0.001f, 0.999f) to EvolaColors.Border,
                        1f to EvolaColors.Border,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(size - 8.dp)
                .clip(CircleShape)
                .background(EvolaColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("$clamped%", style = MaterialTheme.typography.labelSmall, color = EvolaColors.Ink, textAlign = TextAlign.Center)
        }
    }
}

/** Disabled-affordance styling reused wherever a section/option is visible per the design but has
 * no real backend yet ("wire what's real, stub the rest clearly"). */
@Composable
fun ComingSoonChip(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    SelectableChip(label = label, selected = false, onClick = {}, icon = icon, enabled = false, modifier = modifier)
}

@Composable
fun LockedRow(
    label: String,
    subtitle: String,
    icon: ImageVector,
    lockIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(EvolaColors.SurfaceAlt)
            .padding(EvolaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
    ) {
        IconTile(icon = icon, locked = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text3)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        }
        Icon(lockIcon, contentDescription = "Locked - coming soon", tint = EvolaColors.Text3)
    }
}

/** A floating, frosted-glass-style bottom navigation bar - approximates iOS 26's "Liquid Glass"
 * material using [Haze](https://github.com/chrisbanes/haze) for genuine real-time backdrop blur
 * of whatever scrolls behind it (the same technique Slack/Instagram-style blurred bars use),
 * rather than flat translucency. True Liquid Glass is still a native UIKit/SwiftUI-only API and
 * isn't reachable from shared Compose UI without a much bigger native rewrite, but real backdrop
 * blur gets far closer than a translucent color ever could. [hazeState] must be the same instance
 * passed to [Modifier.hazeSource] on the scrollable content behind this bar. */
@Composable
fun GlassNavigationBar(hazeState: HazeState, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.sm)
            .shadow(elevation = 20.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = EvolaColors.Surface,
                    tint = HazeTint(EvolaColors.Surface.copy(alpha = 0.45f)),
                    blurRadius = 24.dp,
                    noiseFactor = 0.08f,
                ),
            )
            // A pure-white hairline (right against the old dark glass bar) is nearly invisible on
            // Reword's light, white-tinted blur - the ink token at low alpha reads on both.
            .border(BorderStroke(1.dp, EvolaColors.Text.copy(alpha = 0.10f)), shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = EvolaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Preview
@Composable
private fun SelectableChipPreview() {
    EvolaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            SelectableChip(label = "PDF", selected = true, onClick = {}, icon = Icons.AutoMirrored.Filled.MenuBook)
            SelectableChip(label = "Text", selected = false, onClick = {})
        }
    }
}

@Preview
@Composable
private fun IconTilePreview() {
    EvolaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            IconTile(icon = Icons.Filled.Star, locked = false)
            IconTile(icon = Icons.Filled.Lock, locked = true)
        }
    }
}

@Preview
@Composable
private fun StatusTagPreview() {
    EvolaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            StatusTag(label = "Ready", style = StatusTagStyle.FILLED)
            StatusTag(label = "In progress", style = StatusTagStyle.OUTLINE)
            StatusTag(label = "Not started", style = StatusTagStyle.NEUTRAL)
        }
    }
}

@Preview
@Composable
private fun SegmentedProgressBarPreview() {
    EvolaTheme {
        SegmentedProgressBar(segmentCount = 4, filledCount = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun CircularProgressRingPreview() {
    EvolaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            CircularProgressRing(percent = 35)
            CircularProgressRing(percent = 80, size = 40.dp)
        }
    }
}

@Preview
@Composable
private fun ComingSoonChipPreview() {
    EvolaTheme {
        ComingSoonChip(label = "Flashcards", icon = Icons.Filled.Star)
    }
}

@Preview
@Composable
private fun LockedRowPreview() {
    EvolaTheme {
        LockedRow(label = "Grammar drills", subtitle = "Coming soon", icon = Icons.AutoMirrored.Filled.MenuBook, lockIcon = Icons.Filled.Lock)
    }
}

@Preview
@Composable
private fun GlassNavigationBarPreview() {
    EvolaTheme {
        val hazeState = remember { HazeState() }
        GlassNavigationBar(hazeState = hazeState) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = EvolaColors.Accent)
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = EvolaColors.Text3)
            Icon(Icons.Filled.Person, contentDescription = null, tint = EvolaColors.Text3)
        }
    }
}
