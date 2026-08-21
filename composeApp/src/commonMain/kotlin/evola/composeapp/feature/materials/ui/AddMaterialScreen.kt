@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.feature.materials.ui

import evola.composeapp.feature.materials.vm.AddMaterialState
import evola.composeapp.feature.materials.vm.AddMaterialViewModel
import evola.composeapp.feature.materials.vm.ResourceType
import evola.composeapp.feature.materials.vm.StagedResource
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
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.materials_add_add_photo_desc
import evola.composeapp.generated.resources.materials_add_cancel
import evola.composeapp.generated.resources.materials_add_choose_different_file
import evola.composeapp.generated.resources.materials_add_choose_from_gallery
import evola.composeapp.generated.resources.materials_add_continue
import evola.composeapp.generated.resources.materials_add_file_hint
import evola.composeapp.generated.resources.materials_add_image_hint
import evola.composeapp.generated.resources.materials_add_photo_sheet_title
import evola.composeapp.generated.resources.materials_add_remove_photo_desc
import evola.composeapp.generated.resources.materials_add_tap_to_choose_file
import evola.composeapp.generated.resources.materials_add_take_photo
import evola.composeapp.generated.resources.materials_add_text_placeholder
import evola.composeapp.generated.resources.materials_add_title
import evola.composeapp.generated.resources.materials_add_type_image
import evola.composeapp.generated.resources.materials_add_type_pdf
import evola.composeapp.generated.resources.materials_add_type_text
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.EvolaCard
import evola.composeapp.core.designsystem.components.SelectableChip
import evola.shared.feature.materials.domain.MIN_EXTRACTABLE_TEXT_LENGTH
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun AddMaterialScreen(
    viewModel: AddMaterialViewModel,
    onContinue: (StagedResource) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.collectAsState()
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var pastedText by remember { mutableStateOf("") }
    var pickedImages by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val launchPicker = rememberFilePicker { pickedFile = it }
    val launchImagePicker = rememberImagePicker { pickedImages = pickedImages + it }
    val launchCamera = rememberCameraCapture { pickedImages = pickedImages + it }

    if (showImageSourceDialog) {
        ImageSourceSheet(
            onCamera = launchCamera,
            onGallery = launchImagePicker,
            onDismiss = { showImageSourceDialog = false },
        )
    }

    AddMaterialContent(
        state = state,
        pickedFile = pickedFile,
        pastedText = pastedText,
        onPastedTextChange = { pastedText = it },
        pickedImages = pickedImages,
        onAddImage = { showImageSourceDialog = true },
        onRemoveImage = { index -> pickedImages = pickedImages.filterIndexed { i, _ -> i != index } },
        onLaunchFilePicker = launchPicker,
        onSelectType = { type -> viewModel.selectType(type) },
        onContinue = onContinue,
        onCancel = onCancel,
    )
}

@Composable
private fun AddMaterialContent(
    state: AddMaterialState,
    pickedFile: PickedFile?,
    pastedText: String,
    onPastedTextChange: (String) -> Unit,
    pickedImages: List<PickedFile>,
    onAddImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onLaunchFilePicker: () -> Unit,
    onSelectType: (ResourceType) -> Unit,
    onContinue: (StagedResource) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedType = state.selectedType
    val focusManager = LocalFocusManager.current
    val canContinue = when (selectedType) {
        ResourceType.PDF -> pickedFile != null
        ResourceType.TEXT -> pastedText.trim().length >= MIN_EXTRACTABLE_TEXT_LENGTH
        ResourceType.IMAGE -> pickedImages.isNotEmpty()
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.materials_add_title)) }, scrollBehavior = scrollBehavior) },
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
                        Text(stringResource(Res.string.materials_add_continue))
                    }
                    Spacer(Modifier.height(EvolaSpacing.sm))
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.materials_add_cancel))
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
                        label = stringResource(Res.string.materials_add_type_pdf),
                        selected = selectedType == ResourceType.PDF,
                        onClick = { onSelectType(ResourceType.PDF) },
                        icon = Icons.Filled.PictureAsPdf,
                        modifier = Modifier.weight(1f),
                    )
                    SelectableChip(
                        label = stringResource(Res.string.materials_add_type_text),
                        selected = selectedType == ResourceType.TEXT,
                        onClick = { onSelectType(ResourceType.TEXT) },
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f),
                    )
                    SelectableChip(
                        label = stringResource(Res.string.materials_add_type_image),
                        selected = selectedType == ResourceType.IMAGE,
                        onClick = { onSelectType(ResourceType.IMAGE) },
                        icon = Icons.Filled.Image,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (selectedType) {
                    ResourceType.PDF -> {
                        val current = pickedFile
                        if (current == null) {
                            // EvolaCard's internal padding is fixed at EvolaSpacing.lg (not this
                            // placeholder's original xxl "big dropzone" padding) - a small,
                            // deliberate visual shrink accepted for real component reuse here.
                            EvolaCard(
                                onClick = onLaunchFilePicker,
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = EvolaColors.SurfaceAlt,
                                bordered = true,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(stringResource(Res.string.materials_add_tap_to_choose_file), style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(EvolaSpacing.xs))
                                    Text(
                                        stringResource(Res.string.materials_add_file_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EvolaColors.Text2,
                                    )
                                }
                            }
                        } else {
                            EvolaCard(modifier = Modifier.fillMaxWidth()) {
                                Text(current.fileName, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(EvolaSpacing.sm))
                                TextButton(onClick = onLaunchFilePicker) {
                                    Text(stringResource(Res.string.materials_add_choose_different_file))
                                }
                            }
                        }
                    }

                    ResourceType.TEXT -> {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = onPastedTextChange,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            placeholder = { Text(stringResource(Res.string.materials_add_text_placeholder)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        )
                    }

                    ResourceType.IMAGE -> {
                        Text(
                            stringResource(Res.string.materials_add_image_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = EvolaColors.Text2,
                        )
                        Spacer(Modifier.height(EvolaSpacing.md))
                        ImagePickerGrid(
                            images = pickedImages,
                            onAdd = onAddImage,
                            onRemove = onRemoveImage,
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
            Text(stringResource(Res.string.materials_add_photo_sheet_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.md))
            ImageSourceOption(icon = Icons.Filled.CameraAlt, label = stringResource(Res.string.materials_add_take_photo), onClick = { dismissThen(onCamera) })
            ImageSourceOption(icon = Icons.Filled.PhotoLibrary, label = stringResource(Res.string.materials_add_choose_from_gallery), onClick = { dismissThen(onGallery) })
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
                        Spacer(Modifier.height(EvolaSpacing.xs))
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
                        contentDescription = stringResource(Res.string.materials_add_remove_photo_desc),
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
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.materials_add_add_photo_desc), tint = EvolaColors.Text2)
            }
        }
    }
}

@Preview
@Composable
private fun AddMaterialPdfEmptyPreview() {
    EvolaTheme {
        AddMaterialContent(
            state = AddMaterialState(selectedType = ResourceType.PDF), pickedFile = null, pastedText = "", onPastedTextChange = {},
            pickedImages = emptyList(), onAddImage = {}, onRemoveImage = {}, onLaunchFilePicker = {}, onSelectType = {}, onContinue = {}, onCancel = {},
        )
    }
}

@Preview
@Composable
private fun AddMaterialPdfPickedPreview() {
    EvolaTheme {
        AddMaterialContent(
            state = AddMaterialState(selectedType = ResourceType.PDF),
            pickedFile = PickedFile(fileName = "grammar-book.pdf", mimeType = "application/pdf", bytes = ByteArray(0)),
            pastedText = "", onPastedTextChange = {}, pickedImages = emptyList(), onAddImage = {}, onRemoveImage = {},
            onLaunchFilePicker = {}, onSelectType = {}, onContinue = {}, onCancel = {},
        )
    }
}

@Preview
@Composable
private fun AddMaterialTextPreview() {
    EvolaTheme {
        AddMaterialContent(
            state = AddMaterialState(selectedType = ResourceType.TEXT), pickedFile = null, pastedText = "Some pasted text to analyze.",
            onPastedTextChange = {}, pickedImages = emptyList(), onAddImage = {}, onRemoveImage = {},
            onLaunchFilePicker = {}, onSelectType = {}, onContinue = {}, onCancel = {},
        )
    }
}

@Preview
@Composable
private fun AddMaterialImagePreview() {
    EvolaTheme {
        AddMaterialContent(
            state = AddMaterialState(selectedType = ResourceType.IMAGE), pickedFile = null, pastedText = "",
            onPastedTextChange = {},
            pickedImages = listOf(PickedFile(fileName = "page1.jpg", mimeType = "image/jpeg", bytes = ByteArray(0))),
            onAddImage = {}, onRemoveImage = {}, onLaunchFilePicker = {}, onSelectType = {}, onContinue = {}, onCancel = {},
        )
    }
}
