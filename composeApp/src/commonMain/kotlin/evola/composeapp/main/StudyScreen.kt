package evola.composeapp.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Study tab per 06_SCREENS_REFERENCE.md screen #11 (Lesson Selection List) - lands here once
 * Automatic Lesson Generation (M4) exists. Honest "not ready yet" state until then. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Study") }) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Lessons aren't ready yet. Upload a material on the Materials tab to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
