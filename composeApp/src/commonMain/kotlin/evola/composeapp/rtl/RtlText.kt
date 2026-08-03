package evola.composeapp.rtl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import evola.composeapp.theme.arabicFamily

/** Renders Arabic (or any RTL-script) text: forces RTL layout direction, right-alignment, and the
 * dedicated Arabic-capable font - neither of the app's Latin fonts (Inter/Fraunces) contain Arabic
 * glyphs, so this is required everywhere Arabic vocabulary/translations appear, not just a visual
 * nicety. */
@Composable
fun RtlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = text,
                style = style.copy(fontFamily = arabicFamily(), textAlign = TextAlign.Right),
                color = color,
            )
        }
    }
}
