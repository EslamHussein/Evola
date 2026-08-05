package evola.composeapp.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.fraunces_variable
import evola.composeapp.generated.resources.ibm_plex_mono_medium
import evola.composeapp.generated.resources.ibm_plex_mono_semibold
import evola.composeapp.generated.resources.inter_variable
import evola.composeapp.generated.resources.noto_naskh_arabic_variable
import org.jetbrains.compose.resources.Font

/**
 * Dark "Nocturne" palette (the design the app now targets). The token *names* are kept semantic so
 * the ~11 screens referencing them re-skin from these values alone: [Paper] = deepest app
 * background, [Surface]/[SurfaceAlt] = elevated card/input surfaces, [Text]/[Text2]/[Text3] =
 * primary/secondary/muted foreground, and the accent (formerly gold) is now purple — reused by
 * [Gold]/[GoldSoft] so progress rings, selected chips, and status badges pick it up automatically.
 */
object EvolaColors {
    val Paper = Color(0xFF0E1220)      // deepest app background
    val Surface = Color(0xFF1A2030)    // card surface
    val SurfaceAlt = Color(0xFF232B3D) // input / elevated surface
    val Border = Color(0xFF2B3345)     // hairline borders

    val Accent = Color(0xFF7C6CF0)     // primary purple accent
    val AccentSoft = Color(0xFF262149) // selected / soft accent fill
    // Legacy names remapped to the accent so existing screens re-skin without edits.
    val Gold = Accent
    val GoldSoft = AccentSoft
    val Ink = Accent
    val Ink2 = Color(0xFF9A93F5)

    val Teal = Color(0xFF4FB6A6)       // success
    val TealSoft = Color(0xFF16302C)
    val Rust = Color(0xFFE0715C)       // error
    val RustSoft = Color(0xFF3A211C)

    val Text = Color(0xFFEEF1F8)       // primary text
    val Text2 = Color(0xFFAAB2C6)      // secondary text
    val Text3 = Color(0xFF6E7488)      // muted text
}

@Composable
private fun frauncesFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_variable, weight = FontWeight.Normal),
    Font(Res.font.fraunces_variable, weight = FontWeight.Medium),
    Font(Res.font.fraunces_variable, weight = FontWeight.SemiBold),
    Font(Res.font.fraunces_variable, weight = FontWeight.Bold),
)

@Composable
private fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_variable, weight = FontWeight.Normal),
    Font(Res.font.inter_variable, weight = FontWeight.Medium),
    Font(Res.font.inter_variable, weight = FontWeight.SemiBold),
    Font(Res.font.inter_variable, weight = FontWeight.Bold),
)

@Composable
private fun plexMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    Font(Res.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
)

/** Neither Inter nor Fraunces (the app's Latin fonts) contain any Arabic glyphs (confirmed via
 * font cmap inspection) - Noto Naskh Arabic is the dedicated family for the Arabic vocabulary
 * translations/RTL text introduced in the design-handoff redesign. */
@Composable
internal fun arabicFamily(): FontFamily = FontFamily(
    Font(Res.font.noto_naskh_arabic_variable, weight = FontWeight.Normal),
    Font(Res.font.noto_naskh_arabic_variable, weight = FontWeight.Bold),
)

/** IBM Plex Mono readout style for numeric data (percentages, scores, timestamps) - Material3's
 * Typography has no built-in "mono" slot, so this is applied ad hoc where needed. */
object EvolaTypography {
    @Composable
    fun dataMono(fontSize: androidx.compose.ui.unit.TextUnit = 16.sp): TextStyle = TextStyle(
        fontFamily = plexMonoFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
    )
}

private fun evolaColorScheme() = darkColorScheme(
    primary = EvolaColors.Accent,
    onPrimary = Color(0xFFFFFFFF),
    secondary = EvolaColors.Accent,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = EvolaColors.Teal,
    onTertiary = Color(0xFF06201C),
    // background = deepest navy; surface kept equal so full-screen Surface() paints the same base,
    // while cards use surfaceVariant / EvolaColors.Surface for the lighter elevated tone.
    background = EvolaColors.Paper,
    onBackground = EvolaColors.Text,
    surface = EvolaColors.Paper,
    onSurface = EvolaColors.Text,
    surfaceVariant = EvolaColors.Surface,
    onSurfaceVariant = EvolaColors.Text2,
    error = EvolaColors.Rust,
    onError = Color(0xFF2A0F0A),
    errorContainer = EvolaColors.RustSoft,
    onErrorContainer = EvolaColors.Rust,
    outline = EvolaColors.Border,
    outlineVariant = EvolaColors.Border,
)

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
fun EvolaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = evolaColorScheme(),
        typography = evolaTypography(),
        shapes = evolaShapes(),
        content = content,
    )
}
