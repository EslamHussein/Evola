package evola.composeapp.a11y

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Mirrors Android's own "Remove animations" accessibility setting, which zeroes the animator
 * duration scale - the same signal Android itself uses to decide whether to play system
 * animations. Read once per composition; this setting doesn't change while a loading indicator is
 * on screen. */
@Composable
actual fun isReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
