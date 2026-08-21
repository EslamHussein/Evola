package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** The app's one card container - built on M3's real [Card]/[OutlinedCard] rather than a hand-rolled
 * `Surface`, so elevation/ripple (when [onClick] is set) are M3's own tested implementation. Fixed
 * `shape = MaterialTheme.shapes.medium` and internal [EvolaSpacing.lg] content padding, so callers
 * stop repeating either - drop any `Modifier.padding(EvolaSpacing.lg)` a call site had around its
 * own content when migrating onto this. [bordered] switches to [OutlinedCard] (M3's own idiom for a
 * bordered card, rather than adding a border to the plain variant); [onClick] routes to the
 * clickable Card/OutlinedCard overload when non-null. */
@Composable
fun EvolaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = EvolaColors.Surface,
    bordered: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val innerModifier = Modifier.padding(EvolaSpacing.lg)
    if (bordered) {
        val colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
        if (onClick != null) {
            OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
                Column(modifier = innerModifier, content = content)
            }
        } else {
            OutlinedCard(modifier = modifier, shape = shape, colors = colors) {
                Column(modifier = innerModifier, content = content)
            }
        }
    } else {
        val colors = CardDefaults.cardColors(containerColor = containerColor)
        if (onClick != null) {
            Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
                Column(modifier = innerModifier, content = content)
            }
        } else {
            Card(modifier = modifier, shape = shape, colors = colors) {
                Column(modifier = innerModifier, content = content)
            }
        }
    }
}

@Preview
@Composable
private fun EvolaCardPreview() {
    EvolaTheme {
        Column(
            modifier = Modifier.background(EvolaColors.Paper).padding(EvolaSpacing.lg),
        ) {
            EvolaCard {
                Text("Plain card", color = EvolaColors.Text)
            }
            EvolaCard(modifier = Modifier.padding(top = EvolaSpacing.md), containerColor = EvolaColors.SurfaceAlt) {
                Text("Tinted container", color = EvolaColors.Text)
            }
            EvolaCard(modifier = Modifier.padding(top = EvolaSpacing.md), bordered = true) {
                Text("Bordered card", color = EvolaColors.Text)
            }
            EvolaCard(modifier = Modifier.padding(top = EvolaSpacing.md), onClick = {}) {
                Text("Clickable card", color = EvolaColors.Text)
            }
        }
    }
}
