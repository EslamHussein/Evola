@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.SelectableChip

private const val MIN_PASTED_TEXT_LENGTH = 20

@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel,
    onContinue: (StagedResource) -> Unit,
    onCancel: () -> Unit,
) {
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var pastedText by remember { mutableStateOf("") }
    val launchPicker = rememberFilePicker { pickedFile = it }

    val canContinue = when (selectedType) {
        ResourceType.PDF -> pickedFile != null
        ResourceType.TEXT -> pastedText.trim().length >= MIN_PASTED_TEXT_LENGTH
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add material") }) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
                    Button(
                        onClick = {
                            val staged = when (selectedType) {
                                ResourceType.PDF -> pickedFile?.let {
                                    StagedResource.File(it.fileName, it.mimeType, it.bytes)
                                }
                                ResourceType.TEXT -> StagedResource.Text(pastedText.trim())
                            }
                            staged?.let(onContinue)
                        },
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue")
                    }
                    Spacer(Modifier.height(EvolaSpacing.sm))
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text("What are you adding?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(EvolaSpacing.md))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                    SelectableChip(
                        label = "PDF",
                        selected = selectedType == ResourceType.PDF,
                        onClick = { viewModel.selectType(ResourceType.PDF) },
                        icon = Icons.Filled.PictureAsPdf,
                        modifier = Modifier.weight(1f),
                    )
                    SelectableChip(
                        label = "Text",
                        selected = selectedType == ResourceType.TEXT,
                        onClick = { viewModel.selectType(ResourceType.TEXT) },
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (selectedType) {
                    ResourceType.PDF -> {
                        val current = pickedFile
                        if (current == null) {
                            Surface(
                                onClick = launchPicker,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = EvolaColors.SurfaceAlt,
                                border = BorderStroke(1.dp, EvolaColors.Border),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.xxl),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("Tap to choose a file", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(EvolaSpacing.xs))
                                    Text(
                                        "PDF or Word document, up to 25MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EvolaColors.Text2,
                                    )
                                }
                            }
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
                                    Text(current.fileName, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(EvolaSpacing.sm))
                                    TextButton(onClick = launchPicker) {
                                        Text("Choose a different file")
                                    }
                                }
                            }
                        }
                    }

                    ResourceType.TEXT -> {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            placeholder = { Text("Paste or type your text here...") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        )
                    }
                }
            }
        }
    }
}
