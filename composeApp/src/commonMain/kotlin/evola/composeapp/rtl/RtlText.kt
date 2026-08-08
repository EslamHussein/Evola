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
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.theme.arabicFamily

/** Renders text in the learner's chosen native language ([LocalNativeLanguage]): RTL layout,
 * right-alignment, and the dedicated Arabic-script font for RTL languages (Arabic today; neither of
 * the app's Latin fonts contain those glyphs), or a plain LTR `Text` for everything else. Every
 * translation/native-language call site uses this instead of branching itself. */
@Composable
fun RtlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val nativeLanguage = LocalNativeLanguage.current
    if (!nativeLanguage.isRtl) {
        Text(text = text, modifier = modifier, style = style, color = color)
        return
    }
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
