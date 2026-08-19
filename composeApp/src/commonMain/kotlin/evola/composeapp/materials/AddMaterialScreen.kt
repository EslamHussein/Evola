@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.materials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.SelectableChip
import evola.shared.materials.MIN_EXTRACTABLE_TEXT_LENGTH
import pro.respawn.flowmvi.compose.dsl.subscribe

@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel,
    onContinue: (StagedResource) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.subscribe()
    val selectedType = state.selectedType
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var pastedText by remember { mutableStateOf("") }
    var pickedImages by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val launchPicker = rememberFilePicker { pickedFile = it }
    val launchImagePicker = rememberImagePicker { pickedImages = pickedImages + it }
    val launchCamera = rememberCameraCapture { pickedImages = pickedImages + it }
    val focusManager = LocalFocusManager.current

    if (showImageSourceDialog) {
        ImageSourceSheet(
            onCamera = launchCamera,
            onGallery = launchImagePicker,
            onDismiss = { showImageSourceDialog = false },
        )
    }

    val canContinue = when (selectedType) {
        ResourceType.PDF -> pickedFile != null
        ResourceType.TEXT -> pastedText.trim().length >= MIN_EXTRACTABLE_TEXT_LENGTH
        ResourceType.IMAGE -> pickedImages.isNotEmpty()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Add material") }, scrollBehavior = scrollBehavior) },
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
                                ResourceType.IMAGE -> pickedImages.takeIf { it.isNotEmpty() }?.let { StagedResource.Images(it) }
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
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusManager.clearFocus() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(EvolaSpacing.lg),
            ) {
                Text("What are you adding?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(EvolaSpacing.md))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                    SelectableChip(
                        label = "PDF",
                        selected = selectedType == ResourceType.PDF,
                        onClick = { viewModel.intent(AddMaterialIntent.SelectType(ResourceType.PDF)) },
                        icon = Icons.Filled.PictureAsPdf,
                        modifier = Modifier.weight(1f),
                    )
                    SelectableChip(
                        label = "Text",
                        selected = selectedType == ResourceType.TEXT,
                        onClick = { viewModel.intent(AddMaterialIntent.SelectType(ResourceType.TEXT)) },
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f),
                    )
                    SelectableChip(
                        label = "Image",
                        selected = selectedType == ResourceType.IMAGE,
                        onClick = { viewModel.intent(AddMaterialIntent.SelectType(ResourceType.IMAGE)) },
                        icon = Icons.Filled.Image,
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

                    ResourceType.IMAGE -> {
                        Text(
                            "Photograph pages of your book or notes - we'll read the text for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvolaColors.Text2,
                        )
                        Spacer(Modifier.height(EvolaSpacing.md))
                        ImagePickerGrid(
                            images = pickedImages,
                            onAdd = { showImageSourceDialog = true },
                            onRemove = { index -> pickedImages = pickedImages.filterIndexed { i, _ -> i != index } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSourceSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Runs the source action only after the sheet has finished its dismiss animation, so the
    // camera/gallery launch doesn't visually fight the sheet still sliding away underneath it.
    fun dismissThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.sm),
        ) {
            Text("Add a photo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.md))
            ImageSourceOption(icon = Icons.Filled.CameraAlt, label = "Take photo", onClick = { dismissThen(onCamera) })
            ImageSourceOption(icon = Icons.Filled.PhotoLibrary, label = "Choose from gallery", onClick = { dismissThen(onGallery) })
            Spacer(Modifier.height(EvolaSpacing.md))
        }
    }
}

@Composable
private fun ImageSourceOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = EvolaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = EvolaColors.Accent)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private val IMAGE_TILE_SIZE = 96.dp

@Composable
private fun ImagePickerGrid(images: List<PickedFile>, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        images.forEachIndexed { index, image ->
            Box(modifier = Modifier.size(IMAGE_TILE_SIZE)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.small,
                    color = EvolaColors.SurfaceAlt,
                    border = BorderStroke(1.dp, EvolaColors.Border),
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = EvolaColors.Text3, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            image.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = EvolaColors.Text3,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove photo",
                        tint = EvolaColors.Text,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Surface(
            onClick = onAdd,
            modifier = Modifier.size(IMAGE_TILE_SIZE),
            shape = MaterialTheme.shapes.small,
            color = EvolaColors.SurfaceAlt,
            border = BorderStroke(1.dp, EvolaColors.Border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Add photo", tint = EvolaColors.Text2)
            }
        }
    }
}
