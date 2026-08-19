package evola.composeapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import evola.shared.local.AppTheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.inter_variable
import evola.composeapp.generated.resources.noto_naskh_arabic_variable
import org.jetbrains.compose.resources.Font

/**
 * Reword-inspired light palette. The token *names* are kept semantic (unchanged from the earlier
 * "Nocturne" dark palette this replaces) so the ~11 screens referencing them re-skin from these
 * values alone: [Paper] = deepest app background, [Surface]/[SurfaceAlt] = elevated card/input
 * surfaces, [Text]/[Text2]/[Text3] = primary/secondary/muted foreground. [Accent] is Reword's own
 * periwinkle indigo - its logo/CTA/selected-state color, reverse-engineered from the live app
 * (see the Reword teardown). [Gold]/[Amber]/[Teal]/[Rust] are a light-theme semantic set (success/
 * in-progress/mastered/error) distinct from [Accent] itself, rather than remapped to it.
 */
data class EvolaColorPalette(
    val Paper: Color,
    val Surface: Color,
    val SurfaceAlt: Color,
    val Border: Color,
    val Accent: Color,
    val AccentSoft: Color,
    val Gold: Color,
    val GoldSoft: Color,
    val Ink: Color,
    val Ink2: Color,
    val Teal: Color,
    val TealSoft: Color,
    val Rust: Color,
    val RustSoft: Color,
    val Amber: Color,
    val AmberSoft: Color,
    val Text: Color,
    val Text2: Color,
    val Text3: Color,
    val GenderMasculine: Color,
    val GenderFeminine: Color,
    val GenderNeuter: Color,
)

/** Reword-inspired light palette - see [EvolaColorPalette]'s field docs at each call site for what
 * each token means; [LightEvolaColors] is the original values this app shipped with. */
val LightEvolaColors = EvolaColorPalette(
    Paper = Color(0xFFEFEEF3),      // Reword's own canvas: pale lavender-grey
    Surface = Color(0xFFFFFFFF),    // card surface: white
    SurfaceAlt = Color(0xFFF5F5FA), // input / elevated surface: faint lavender tint
    Border = Color(0xFFDCDEE6),     // hairline borders
    Accent = Color(0xFF5B6EF5),     // Reword's periwinkle indigo - logo, CTAs, selected state
    AccentSoft = Color(0xFFE9EAFC), // selected / soft accent fill
    Gold = Color(0xFFB5690B),       // success / correct-answer highlight (warm amber)
    GoldSoft = Color(0xFFFAF0DD),
    Ink = Color(0xFF5B6EF5),
    Ink2 = Color(0xFF8B82C9),       // secondary "in progress" tone, lighter/greyer than Accent
    Teal = Color(0xFF1C7A52),       // mastered / success
    TealSoft = Color(0xFFE6F5EE),
    Rust = Color(0xFFB23434),       // error / struggling
    RustSoft = Color(0xFFFBE9E9),
    Amber = Color(0xFFC98A1D),      // in-progress / learning tier
    AmberSoft = Color(0xFFFBF0DC),
    Text = Color(0xFF1C1E27),       // primary text: near-black
    Text2 = Color(0xFF4A4D5A),      // secondary text
    Text3 = Color(0xFF8B8E9E),      // muted text
    GenderMasculine = Color(0xFF3B6FE0), // der - blue
    GenderFeminine = Color(0xFFD64550),  // die - red
    GenderNeuter = Color(0xFF1C7A52),    // das - green
)

/** Dark counterpart - same semantic roles, tuned for a dark canvas rather than a naive inversion
 * (accents/semantic hues lightened to keep contrast on a dark ground, per-token, not globally). */
val DarkEvolaColors = EvolaColorPalette(
    Paper = Color(0xFF14151C),
    Surface = Color(0xFF1C1E27),
    SurfaceAlt = Color(0xFF232530),
    Border = Color(0xFF32354A),
    Accent = Color(0xFF7C8CFF),
    AccentSoft = Color(0xFF2A2D57),
    Gold = Color(0xFFE0A040),
    GoldSoft = Color(0xFF3A2E1A),
    Ink = Color(0xFF7C8CFF),
    Ink2 = Color(0xFF9A93D0),
    Teal = Color(0xFF4CC38A),
    TealSoft = Color(0xFF163828),
    Rust = Color(0xFFE0685E),
    RustSoft = Color(0xFF3A1E1E),
    Amber = Color(0xFFE0B04D),
    AmberSoft = Color(0xFF3A2E14),
    Text = Color(0xFFF2F1F5),
    Text2 = Color(0xFFB8BAC8),
    Text3 = Color(0xFF83869A),
    GenderMasculine = Color(0xFF7DA0FF),
    GenderFeminine = Color(0xFFE87F86),
    GenderNeuter = Color(0xFF4CC38A),
)

val LocalEvolaColors = staticCompositionLocalOf { LightEvolaColors }

/** Kept as a property (not a renamed val) so every existing `EvolaColors.Text2`-style call site
 * across the app's ~20 screens is unaffected by the dark-mode rollout - only resolves through
 * [LocalEvolaColors], so it's callable exclusively from composable context (true everywhere it's
 * used already, since it's always screen-render code). */
val EvolaColors: EvolaColorPalette
    @Composable get() = LocalEvolaColors.current

@Composable
private fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_variable, weight = FontWeight.Normal),
    Font(Res.font.inter_variable, weight = FontWeight.Medium),
    Font(Res.font.inter_variable, weight = FontWeight.SemiBold),
    Font(Res.font.inter_variable, weight = FontWeight.Bold),
)

/** Neither Inter nor Fraunces (the app's Latin fonts) contain any Arabic glyphs (confirmed via
 * font cmap inspection) - Noto Naskh Arabic is the dedicated family for the Arabic vocabulary
 * translations/RTL text introduced in the design-handoff redesign. */
@Composable
internal fun arabicFamily(): FontFamily = FontFamily(
    Font(Res.font.noto_naskh_arabic_variable, weight = FontWeight.Normal),
    Font(Res.font.noto_naskh_arabic_variable, weight = FontWeight.Bold),
)

private fun evolaColorScheme(colors: EvolaColorPalette, dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = colors.Accent,
        onPrimary = Color(0xFFFFFFFF),
        secondary = colors.Accent,
        onSecondary = Color(0xFFFFFFFF),
        tertiary = colors.Accent,
        onTertiary = Color(0xFFFFFFFF),
        background = colors.Paper,
        onBackground = colors.Text,
        surface = colors.Paper,
        onSurface = colors.Text,
        surfaceVariant = colors.Surface,
        onSurfaceVariant = colors.Text2,
        surfaceDim = colors.Paper,
        surfaceBright = colors.SurfaceAlt,
        surfaceContainerLowest = colors.Paper,
        surfaceContainerLow = colors.Paper,
        surfaceContainer = colors.Surface,
        surfaceContainerHigh = colors.Surface,
        surfaceContainerHighest = colors.Surface,
        error = colors.Rust,
        onError = Color(0xFFFFFFFF),
        errorContainer = colors.RustSoft,
        onErrorContainer = colors.Rust,
        outline = colors.Border,
        outlineVariant = colors.Border,
    )
} else {
    lightColorScheme(
        primary = colors.Accent,
        onPrimary = Color(0xFFFFFFFF),
        secondary = colors.Accent,
        onSecondary = Color(0xFFFFFFFF),
        // Accent labels/links (e.g. section headers, "Continue") pick up Reword's indigo.
        tertiary = colors.Accent,
        onTertiary = Color(0xFFFFFFFF),
        // background = the pale lavender canvas; surface kept equal so full-screen Surface() paints
        // the same base, while cards use surfaceVariant / colors.Surface for the white elevated tone.
        background = colors.Paper,
        onBackground = colors.Text,
        surface = colors.Paper,
        onSurface = colors.Text,
        surfaceVariant = colors.Surface,
        onSurfaceVariant = colors.Text2,
        // Left unset, these auto-derive from a generic Material neutral-gray algorithm - disconnected
        // from this palette (confirmed via decompiling CardDefaults: plain Card() reads
        // surfaceContainerHighest, which is exactly what was rendering as an off-brand gray). Pinning
        // the whole tonal ladder to the two designed elevated tones makes every M3 component that
        // reads a surfaceContainer* role (Card, BottomSheet, Menu, scrolled TopAppBar, ...) inherit
        // the correct palette instead, with no per-call-site overrides needed.
        surfaceDim = colors.Paper,
        surfaceBright = colors.SurfaceAlt,
        surfaceContainerLowest = colors.Paper,
        surfaceContainerLow = colors.Paper,
        surfaceContainer = colors.Surface,
        surfaceContainerHigh = colors.Surface,
        surfaceContainerHighest = colors.Surface,
        error = colors.Rust,
        onError = Color(0xFFFFFFFF),
        errorContainer = colors.RustSoft,
        onErrorContainer = colors.Rust,
        outline = colors.Border,
        outlineVariant = colors.Border,
    )
}

@Composable
private fun evolaTypography(): Typography {
    // The Nocturne design uses a bold sans across the board (no serif display face).
    val display = interFamily()
    val body = interFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = body, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = body, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}

/** Base radius 18-26px on cards, 100px (pill) on buttons/badges per 05_DESIGN_SYSTEM.md. */
private fun evolaShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(100.dp),
)

@Composable
fun EvolaTheme(appTheme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val palette = if (dark) DarkEvolaColors else LightEvolaColors
    CompositionLocalProvider(LocalEvolaColors provides palette) {
        MaterialTheme(
            colorScheme = evolaColorScheme(palette, dark),
            typography = evolaTypography(),
            shapes = evolaShapes(),
            content = content,
        )
    }
}
