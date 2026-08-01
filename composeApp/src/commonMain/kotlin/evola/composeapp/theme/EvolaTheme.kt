package evola.composeapp.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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
import org.jetbrains.compose.resources.Font

/** Exact hex values from 05_DESIGN_SYSTEM.md. Gold is reserved for progress/readiness only. */
object EvolaColors {
    val Ink = Color(0xFF16213A)
    val Ink2 = Color(0xFF2B3B5C)
    val Paper = Color(0xFFF3EEE1)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF1EDE2)
    val Gold = Color(0xFFC79A3D)
    val GoldSoft = Color(0xFFF2E4BE)
    val Teal = Color(0xFF2F6E68)
    val TealSoft = Color(0xFFDCEAE7)
    val Rust = Color(0xFF9C4A34)
    val RustSoft = Color(0xFFF1DFD8)
    val Text = Color(0xFF16213A)
    val Text2 = Color(0xFF63697A)
    val Text3 = Color(0xFF9297A3)
    val Border = Color(0xFFE4DFD1)
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

private fun evolaColorScheme() = lightColorScheme(
    primary = EvolaColors.Ink,
    onPrimary = EvolaColors.Surface,
    secondary = EvolaColors.Gold,
    onSecondary = EvolaColors.Ink,
    tertiary = EvolaColors.Teal,
    onTertiary = EvolaColors.Surface,
    background = EvolaColors.Paper,
    onBackground = EvolaColors.Text,
    surface = EvolaColors.Surface,
    onSurface = EvolaColors.Text,
    surfaceVariant = EvolaColors.SurfaceAlt,
    onSurfaceVariant = EvolaColors.Text2,
    error = EvolaColors.Rust,
    onError = EvolaColors.Surface,
    errorContainer = EvolaColors.RustSoft,
    onErrorContainer = EvolaColors.Rust,
    outline = EvolaColors.Border,
)

@Composable
private fun evolaTypography(): Typography {
    val display = frauncesFamily()
    val body = interFamily()
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = FontWeight.Medium),
        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.Medium),
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
