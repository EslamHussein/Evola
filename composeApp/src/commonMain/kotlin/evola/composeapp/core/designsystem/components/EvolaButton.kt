package evola.composeapp.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import androidx.compose.ui.tooling.preview.Preview

/** Height/padding/icon-size/type scale shared by every [EvolaButton]-family role - the one base
 * every variant below inherits, so a size change here (e.g. bumping [Medium]'s height) moves every
 * button in the app at once rather than needing a per-screen sweep. */
enum class EvolaButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    Small(40.dp, 16.dp, 16.dp),
    Medium(48.dp, 20.dp, 18.dp),
    Large(56.dp, 24.dp, 20.dp),
}

@Composable
private fun EvolaButtonSize.textStyle() = when (this) {
    EvolaButtonSize.Small -> MaterialTheme.typography.labelMedium
    EvolaButtonSize.Medium, EvolaButtonSize.Large -> MaterialTheme.typography.labelLarge
}

/** Shared base every filled/outlined [EvolaButton]-family role wraps - built on M3's own [Button]
 * rather than hand-rolled on `Surface`, so ripple/state-layer feedback, disabled-alpha (via
 * [colors]), and click semantics are M3's own tested implementation, not a reimplementation. Adds:
 * the app's pill shape uniformly, a focus-visible ring (M3's Button has none built in - needed for
 * external-keyboard/switch-access navigation), and a loading state that swaps the label for a
 * spinner without resizing the button. */
@Composable
private fun EvolaButtonBase(
    onClick: () -> Unit,
    label: String,
    colors: ButtonColors,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    border: BorderStroke? = null,
    leadingIcon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        modifier = modifier
            .height(size.height)
            .then(
                if (focused) {
                    Modifier.border(BorderStroke(2.dp, EvolaColors.BorderStrong), MaterialTheme.shapes.extraLarge)
                } else {
                    Modifier
                },
            ),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.extraLarge,
        colors = colors,
        border = border,
        contentPadding = PaddingValues(horizontal = size.horizontalPadding),
        interactionSource = interactionSource,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(size.iconSize), strokeWidth = 2.dp)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(size.iconSize))
                Spacer(Modifier.width(EvolaSpacing.sm))
            }
            Text(label, style = size.textStyle())
        }
    }
}

/** The one advancing action on a screen. Filled, [EvolaColors.AccentButton]. */
@Composable
fun EvolaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = EvolaColors.AccentButton,
            contentColor = EvolaColors.OnAccentButton,
            disabledContainerColor = EvolaColors.AccentButton.copy(alpha = 0.38f),
            disabledContentColor = EvolaColors.OnAccentButton.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
    )
}

/** Grading/correctness actions ("Got it", "Start learning" in hands-free mode). Filled,
 * [EvolaColors.GoldButton] - Gold is this app's existing correct/positive semantic color, kept as
 * its own role rather than reusing Primary's Accent. */
@Composable
fun EvolaPositiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = EvolaColors.GoldButton,
            contentColor = EvolaColors.OnGoldButton,
            disabledContainerColor = EvolaColors.GoldButton.copy(alpha = 0.38f),
            disabledContentColor = EvolaColors.OnGoldButton.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
    )
}

/** A real alternative to Primary, not a dismissal. Outlined, [EvolaColors.BorderStrong] border. */
@Composable
fun EvolaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = EvolaColors.Text,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = EvolaColors.Text.copy(alpha = 0.38f),
        ),
        border = BorderStroke(1.5.dp, if (enabled) EvolaColors.BorderStrong else EvolaColors.BorderStrong.copy(alpha = 0.38f)),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
    )
}

/** Standalone destructive CTA (the destructive action *is* the screen's purpose) - e.g. a dedicated
 * delete confirmation screen. For a destructive action paired with a low-emphasis dialog Cancel, use
 * [EvolaDestructiveGhostButton] instead. Filled, [EvolaColors.Rust]. */
@Composable
fun EvolaDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = EvolaColors.Rust,
            contentColor = EvolaColors.OnDestructiveButton,
            disabledContainerColor = EvolaColors.Rust.copy(alpha = 0.38f),
            disabledContentColor = EvolaColors.OnDestructiveButton.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
        leadingIcon = leadingIcon,
    )
}

/** Exploratory or supplementary action, never a screen's only path forward. Text-only,
 * [EvolaColors.Accent] content, no fill or border. */
@Composable
fun EvolaGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = EvolaColors.Accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = EvolaColors.Accent.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
    )
}

/** The destructive counterpart to [EvolaGhostButton] - used when Delete sits beside Cancel in a
 * dialog (same visual tier as Ghost, differentiated by hue). This is the role that should back
 * every destructive confirmation's commit button, so an irreversible action always reads as one. */
@Composable
fun EvolaDestructiveGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    EvolaButtonBase(
        onClick = onClick,
        label = text,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = EvolaColors.Rust,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = EvolaColors.Rust.copy(alpha = 0.38f),
        ),
        modifier = modifier,
        size = size,
        enabled = enabled,
        loading = loading,
    )
}

/** Single-icon action: nav back, play, undo, remove. [tonal] = true gives a filled circular tonal
 * background ([EvolaColors.SurfaceAlt]) for a higher-weight icon action (e.g. play pronunciation);
 * false gives a transparent background for a low-weight one (e.g. close, nav back). [contentColor]
 * is left to the caller since icon-button tint varies by context (Accent for play, Text2 for a
 * plain nav icon, Rust for a destructive one) rather than being locked to one semantic role. */
@Composable
fun EvolaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    contentColor: Color = EvolaColors.Text2,
    size: EvolaButtonSize = EvolaButtonSize.Medium,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size.height)
            .then(if (tonal) Modifier.clip(CircleShape).background(EvolaColors.SurfaceAlt) else Modifier),
        enabled = enabled,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
            modifier = Modifier.size(size.iconSize),
        )
    }
}

/** Inline text-as-link with no button chrome, but a real 48dp minimum tap target (via
 * [minimumInteractiveComponentSize]) and [Role.Button] semantics - unlike a bare
 * `Text(...).clickable()`, whose tap target is exactly the glyph bounds. [contentColor] defaults to
 * [EvolaColors.Accent] but callers needing a semantic color (e.g. a Rust/Gold grading footer) pass
 * their own. */
@Composable
fun EvolaTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = EvolaColors.Accent,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Text(
        text,
        color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        style = style,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { role = Role.Button }
            .then(
                if (enabled) {
                    Modifier.clickable(onClickLabel = text, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    )
}

@Preview
@Composable
private fun EvolaButtonGalleryPreview() {
    EvolaTheme {
        Column(
            modifier = Modifier.background(EvolaColors.Paper),
            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
        ) {
            EvolaPrimaryButton(text = "Continue", onClick = {}, size = EvolaButtonSize.Large)
            EvolaPositiveButton(text = "Got it", onClick = {})
            EvolaSecondaryButton(text = "Save as draft", onClick = {})
            EvolaDestructiveButton(text = "Delete account", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                EvolaGhostButton(text = "Learn more", onClick = {})
                EvolaDestructiveGhostButton(text = "Remove", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                EvolaIconButton(icon = Icons.Filled.Close, contentDescription = "Close", onClick = {})
                EvolaIconButton(icon = Icons.Filled.Add, contentDescription = "Play", onClick = {}, tonal = true, contentColor = EvolaColors.Accent)
                EvolaIconButton(icon = Icons.Filled.Delete, contentDescription = "Delete", onClick = {}, contentColor = EvolaColors.Rust)
            }
            EvolaTextLink(text = "Read the full changelog", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                EvolaPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
                EvolaPrimaryButton(text = "Saving", onClick = {}, loading = true)
            }
        }
    }
}
