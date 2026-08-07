package evola.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import evola.composeapp.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicit opt-in rather than relying on Android 15's (targetSdk 35) forced edge-to-edge
        // fallback. The double-status-bar-gap bug this app actually had was a separate issue in
        // MainScreen's nested Scaffold - see the contentWindowInsets comment there.
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
