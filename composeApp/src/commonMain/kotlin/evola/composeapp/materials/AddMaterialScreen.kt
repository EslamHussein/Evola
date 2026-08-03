@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel,
    onUploaded: (materialId: String) -> Unit,
    onCancel: () -> Unit,
) {
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val duplicateMaterialId by viewModel.duplicateMaterialId.collectAsStateWithLifecycle()
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    val launchPicker = rememberFilePicker { pickedFile = it }

    duplicateMaterialId?.let { existingId ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicatePrompt,
            title = { Text("Already uploaded") },
            text = { Text("You've already uploaded this file. Would you like to view it instead?") },
            confirmButton = {
                TextButton(onClick = { onUploaded(existingId) }) { Text("View existing material") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicatePrompt) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add material") }) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    "Upload a PDF or Word document (up to 25MB). We'll pull the text out and turn it into lessons.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                val current = pickedFile
                if (current == null) {
                    OutlinedButton(onClick = launchPicker, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose a PDF or DOCX file")
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(current.fileName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = launchPicker, enabled = !isSubmitting) {
                                Text("Choose a different file")
                            }
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { current?.let { viewModel.submit(it, onUploaded) } },
                    enabled = !isSubmitting && current != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                            Text("Uploading...")
                        }
                    } else {
                        Text("Upload")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
