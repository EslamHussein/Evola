package evola.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import evola.composeapp.core.analytics.installCrashLogging
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    installCrashLogging()
    return ComposeUIViewController { App() }
}
