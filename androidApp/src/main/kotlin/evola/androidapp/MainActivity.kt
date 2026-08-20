package evola.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import evola.composeapp.App
import evola.composeapp.core.analytics.installCrashLogging

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogging()
        // Must be called before super.onCreate() - hands off from the OS-level splash (themed to
        // match evola.composeapp.feature.onboarding.ui.SplashScreen's opening frame) the instant this activity's
        // first frame is ready, then our own Compose splash takes over from there.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Explicit opt-in rather than relying on Android 15's (targetSdk 35) forced edge-to-edge
        // fallback. The double-status-bar-gap bug this app actually had was a separate issue in
        // MainScreen's nested Scaffold - see the contentWindowInsets comment there.
        // Forced light system-bar style (dark icons) rather than SystemBarStyle.auto - this app has
        // exactly one fixed theme (Reword's light palette), not a system-dark-mode-following one, so
        // "auto" would otherwise put light (invisible-on-light-background) status bar icons on a
        // device with system dark mode on.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            App()
        }
    }
}
