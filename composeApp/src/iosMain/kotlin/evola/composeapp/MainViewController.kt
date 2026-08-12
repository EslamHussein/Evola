package evola.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    installCrashLogging()
    return ComposeUIViewController { App() }
}
