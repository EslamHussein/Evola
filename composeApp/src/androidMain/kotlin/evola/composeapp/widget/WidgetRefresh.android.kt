package evola.composeapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberWidgetRefresher(): () -> Unit {
    val context = LocalContext.current
    return { HomeWidgetProvider.requestUpdate(context) }
}
