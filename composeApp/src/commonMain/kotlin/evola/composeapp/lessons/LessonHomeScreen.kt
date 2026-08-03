@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Lesson Home hub per 06_SCREENS_REFERENCE.md screen #12: minimal for M6 - vocabulary entry
 * points only. Grammar gets its own section here in M7, not built yet. */
@Composable
fun LessonHomeScreen(
    lessonTitle: String,
    onStartVocabularySession: () -> Unit,
    onViewVocabularyList: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lessonTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text("Vocabulary", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onStartVocabularySession, modifier = Modifier.fillMaxWidth()) {
                    Text("Start vocabulary session")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onViewVocabularyList, modifier = Modifier.fillMaxWidth()) {
                    Text("View vocabulary list")
                }
            }
        }
    }
}
