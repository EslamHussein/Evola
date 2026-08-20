package evola.composeapp.core.common

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareText(): (String) -> Unit = { text ->
    val activity = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(activity, animated = true, completion = null)
}
