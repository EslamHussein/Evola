@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import evola.composeapp.core.common.ChaseLoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import evola.composeapp.core.common.RtlText
import evola.composeapp.speech.SpeechService
import evola.composeapp.speech.rememberSpeechService
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.vocabulary.rememberCsvFilePicker
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.parseWordCsv
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_cancel
import evola.composeapp.generated.resources.lessons_action_play_pronunciation
import evola.composeapp.generated.resources.lessons_content_desc_more
import evola.composeapp.generated.resources.lessons_content_desc_sort
import evola.composeapp.generated.resources.lessons_empty_vocabulary
import evola.composeapp.generated.resources.lessons_fab_word
import evola.composeapp.generated.resources.lessons_import_button
import evola.composeapp.generated.resources.lessons_ipa_slash
import evola.composeapp.generated.resources.lessons_menu_reset_progress
import evola.composeapp.generated.resources.lessons_nav_back
import evola.composeapp.generated.resources.lessons_no_words_match
import evola.composeapp.generated.resources.lessons_reset_progress_confirm
import evola.composeapp.generated.resources.lessons_reset_progress_text
import evola.composeapp.generated.resources.lessons_reset_progress_title
import evola.composeapp.generated.resources.lessons_search_placeholder
import evola.composeapp.generated.resources.lessons_snackbar_delete_failed
import evola.composeapp.generated.resources.lessons_snackbar_import_failed
import evola.composeapp.generated.resources.lessons_snackbar_imported_word
import evola.composeapp.generated.resources.lessons_snackbar_imported_words
import evola.composeapp.generated.resources.lessons_snackbar_no_valid_rows
import evola.composeapp.generated.resources.lessons_snackbar_progress_reset
import evola.composeapp.generated.resources.lessons_snackbar_progress_reset_failed
import evola.composeapp.generated.resources.lessons_snackbar_update_failed
import evola.composeapp.generated.resources.lessons_sort_alphabetical
import evola.composeapp.generated.resources.lessons_sort_default
import evola.composeapp.generated.resources.lessons_sort_progress
import evola.composeapp.generated.resources.lessons_vocab_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** The vocabulary list screen's own sub-screens live in sibling files in this package -
 * [VocabularyWordDetailScreen.kt] (word detail, also home to the shared `vocabularyStatusStyle`
 * used by [VocabularyRow] below) and [VocabularyEditScreens.kt] (add/edit forms) - kept separate
 * so this file stays focused on the list itself. */
private enum class VocabularySortMode(val labelRes: StringResource) {
    DEFAULT(Res.string.lessons_sort_default),
    ALPHABETICAL(Res.string.lessons_sort_alphabetical),
    PROGRESS(Res.string.lessons_sort_progress),
}

@Composable
fun VocabularyListScreen(viewModel: VocabularyListViewModel, onBack: () -> Unit) {
    val state by viewModel.collectAsState()
    var editingItem by remember { mutableStateOf<VocabularyItem?>(null) }
    var viewingItem by remember { mutableStateOf<VocabularyItem?>(null) }
    var addingWord by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Mirrors the last side effect into a local Compose state (rather than reading it straight
    // from the suspend collectSideEffect callback below) because the imported-word count needs
    // stringResource's plural resolution, which is composable-context-only.
    var lastEffect by remember { mutableStateOf<VocabularyListSideEffect?>(null) }
    var lastEffectId by remember { mutableStateOf(0L) }
    viewModel.collectSideEffect { effect ->
        lastEffect = effect
        lastEffectId++
    }

    // Snackbar copy must be resolved here (composable context) since it's used inside the
    // LaunchedEffect's suspend lambda below, where stringResource cannot be called.
    val updateFailedMsg = stringResource(Res.string.lessons_snackbar_update_failed)
    val importFailedMsg = stringResource(Res.string.lessons_snackbar_import_failed)
    val importedCount = (lastEffect as? VocabularyListSideEffect.WordsImported)?.count
    val importedWordMsg = if (importedCount == 1) {
        stringResource(Res.string.lessons_snackbar_imported_word, importedCount)
    } else {
        stringResource(Res.string.lessons_snackbar_imported_words, importedCount ?: 0)
    }
    val deleteFailedMsg = stringResource(Res.string.lessons_snackbar_delete_failed)
    val progressResetMsg = stringResource(Res.string.lessons_snackbar_progress_reset)
    val progressResetFailedMsg = stringResource(Res.string.lessons_snackbar_progress_reset_failed)

    LaunchedEffect(lastEffectId) {
        when (val effect = lastEffect) {
            is VocabularyListSideEffect.ItemUpdated -> Unit
            is VocabularyListSideEffect.ItemUpdateFailed -> snackbarHostState.showSnackbar(updateFailedMsg)
            is VocabularyListSideEffect.MarkedAlreadyKnown -> viewingItem = null
            is VocabularyListSideEffect.CopiedToPersonalList -> viewingItem = null
            is VocabularyListSideEffect.WordAdded -> addingWord = false
            is VocabularyListSideEffect.WordsImported -> {
                val message = if (effect.count == null) importFailedMsg else importedWordMsg
                snackbarHostState.showSnackbar(message)
            }
            is VocabularyListSideEffect.ItemDeleted -> {
                viewingItem = null
                if (!effect.success) snackbarHostState.showSnackbar(deleteFailedMsg)
            }
            is VocabularyListSideEffect.ProgressReset -> {
                snackbarHostState.showSnackbar(if (effect.success) progressResetMsg else progressResetFailedMsg)
            }
            null -> Unit
        }
    }

    editingItem?.let { item ->
        EditVocabularyScreen(
            item = item,
            onBack = { editingItem = null },
            onSave = { term, meaning, nativeMeaning ->
                viewModel.updateItem(item.itemId, term, meaning, nativeMeaning)
                editingItem = null
            },
        )
        return
    }

    viewingItem?.let { item ->
        VocabularyWordDetailScreen(
            item = item,
            onBack = { viewingItem = null },
            onEdit = {
                viewingItem = null
                editingItem = item
            },
            onMarkAlreadyKnown = { viewModel.markAlreadyKnown(item.itemId) },
            onCopyToPersonalList = { viewModel.copyToPersonalList(item.itemId) },
            onDelete = { viewModel.deleteItem(item.itemId) },
        )
        return
    }

    if (addingWord) {
        AddVocabularyScreen(
            onBack = { addingWord = false },
            onSave = { term, meaning, nativeMeaning -> viewModel.addWord(term, meaning, nativeMeaning) },
        )
        return
    }

    val speechService = rememberSpeechService()
    val noValidRowsMsg = stringResource(Res.string.lessons_snackbar_no_valid_rows)
    val importCsv = rememberCsvFilePicker { text ->
        val rows = parseWordCsv(text)
        if (rows.isEmpty()) {
            coroutineScope.launch { snackbarHostState.showSnackbar(noValidRowsMsg) }
        } else {
            viewModel.importWords(rows)
        }
    }

    VocabularyListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        speechService = speechService,
        onBack = onBack,
        onSelectItem = { viewingItem = it },
        onAddWord = { addingWord = true },
        onImportCsv = importCsv,
        onResetProgress = viewModel::resetProgress,
    )
}

@Composable
private fun VocabularyListContent(
    state: VocabularyListState,
    snackbarHostState: SnackbarHostState,
    speechService: SpeechService,
    onBack: () -> Unit,
    onSelectItem: (VocabularyItem) -> Unit,
    onAddWord: () -> Unit,
    onImportCsv: () -> Unit,
    onResetProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(VocabularySortMode.DEFAULT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(Res.string.lessons_reset_progress_title)) },
            text = { Text(stringResource(Res.string.lessons_reset_progress_text)) },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; onResetProgress() }) { Text(stringResource(Res.string.lessons_reset_progress_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(Res.string.lessons_action_cancel)) }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_vocab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_nav_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(Res.string.lessons_content_desc_sort))
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            VocabularySortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelRes)) },
                                    onClick = { sortMode = mode; sortMenuExpanded = false },
                                    leadingIcon = if (mode == sortMode) {
                                        { Icon(Icons.Filled.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = onImportCsv) { Text(stringResource(Res.string.lessons_import_button)) }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.lessons_content_desc_more))
                        }
                        DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.lessons_menu_reset_progress)) },
                                onClick = { overflowMenuExpanded = false; showResetConfirm = true },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWord,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.lessons_fab_word)) },
                containerColor = EvolaColors.Accent,
                contentColor = Color.White,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state.content) {
                is VocabularyListContent.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChaseLoadingIndicator()
                }

                is VocabularyListContent.Error -> Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is VocabularyListContent.Loaded -> {
                    if (current.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.lessons_empty_vocabulary),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        val filtered = remember(current.items, query, sortMode) {
                            current.items.filter { item ->
                                query.isBlank() ||
                                    item.term.contains(query, ignoreCase = true) ||
                                    item.meaning.contains(query, ignoreCase = true) ||
                                    item.nativeMeaning?.contains(query, ignoreCase = true) == true
                            }.let { list ->
                                when (sortMode) {
                                    VocabularySortMode.DEFAULT -> list
                                    VocabularySortMode.ALPHABETICAL -> list.sortedBy { it.term.lowercase() }
                                    VocabularySortMode.PROGRESS -> list.sortedByDescending {
                                        evola.shared.vocabulary.VocabularySrs.STATUSES.indexOf(it.status)
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(Res.string.lessons_search_placeholder)) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = EvolaColors.Text3) },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = EvolaColors.SurfaceAlt,
                                    unfocusedContainerColor = EvolaColors.SurfaceAlt,
                                    disabledContainerColor = EvolaColors.SurfaceAlt,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.sm),
                            )
                            if (filtered.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.lessons_no_words_match, query), style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = EvolaSpacing.lg)
                                        .clip(MaterialTheme.shapes.large)
                                        .background(EvolaColors.Surface),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
                                ) {
                                    items(filtered, key = { it.itemId }) { item ->
                                        VocabularyRow(
                                            item = item,
                                            onClick = { onSelectItem(item) },
                                            onPlay = { speechService.speak(item.term) },
                                        )
                                        if (item.itemId != filtered.last().itemId) {
                                            HorizontalDivider(color = EvolaColors.Border, thickness = 1.dp, modifier = Modifier.padding(start = EvolaSpacing.lg))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Reword-style compact list row: a thin status-colored rail on the leading edge, the status label
 * in small caps above the term, one meaning line beneath, and a circular pronunciation button on
 * the trailing edge - replacing the earlier per-item floating card (which repeated the same
 * information across a much taller, heavier layout). */
@Composable
private fun VocabularyRow(item: VocabularyItem, onClick: () -> Unit, onPlay: () -> Unit) {
    val (statusColor, statusLabel) = vocabularyStatusStyle(item.status)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = EvolaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(3.dp).height(40.dp).padding(vertical = 2.dp)
                .background(statusColor, shape = RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = EvolaSpacing.md, end = EvolaSpacing.sm)) {
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.term, style = MaterialTheme.typography.titleMedium, color = EvolaColors.Text)
                item.ipaPronunciation?.let {
                    Text(stringResource(Res.string.lessons_ipa_slash, it), style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                }
            }
            val meaningLine = listOfNotNull(item.meaning, item.nativeMeaning?.takeIf { it != item.meaning }).joinToString(", ")
            RtlText(meaningLine, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        }
        Surface(
            onClick = onPlay,
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, EvolaColors.Accent),
            modifier = Modifier.padding(end = EvolaSpacing.md).size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(Res.string.lessons_action_play_pronunciation), tint = EvolaColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val fakeVocabularyItems = listOf(
    VocabularyItem(
        itemId = "1",
        term = "das Haus",
        meaning = "house",
        nativeMeaning = "بيت",
        status = "learning",
        ipaPronunciation = "haʊs",
    ),
    VocabularyItem(
        itemId = "2",
        term = "die Straße",
        meaning = "street",
        nativeMeaning = "شارع",
        status = "new",
    ),
    VocabularyItem(
        itemId = "3",
        term = "laufen",
        meaning = "to run",
        nativeMeaning = "يجري",
        status = "mastered",
    ),
)

@Preview
@Composable
private fun VocabularyListContentLoadingPreview() {
    EvolaTheme {
        VocabularyListContent(
            state = VocabularyListState(content = VocabularyListContent.Loading),
            snackbarHostState = remember { SnackbarHostState() },
            speechService = rememberSpeechService(),
            onBack = {},
            onSelectItem = {},
            onAddWord = {},
            onImportCsv = {},
            onResetProgress = {},
        )
    }
}

@Preview
@Composable
private fun VocabularyListContentLoadedPreview() {
    EvolaTheme {
        VocabularyListContent(
            state = VocabularyListState(content = VocabularyListContent.Loaded(fakeVocabularyItems)),
            snackbarHostState = remember { SnackbarHostState() },
            speechService = rememberSpeechService(),
            onBack = {},
            onSelectItem = {},
            onAddWord = {},
            onImportCsv = {},
            onResetProgress = {},
        )
    }
}

@Preview
@Composable
private fun VocabularyListContentEmptyPreview() {
    EvolaTheme {
        VocabularyListContent(
            state = VocabularyListState(content = VocabularyListContent.Loaded(emptyList())),
            snackbarHostState = remember { SnackbarHostState() },
            speechService = rememberSpeechService(),
            onBack = {},
            onSelectItem = {},
            onAddWord = {},
            onImportCsv = {},
            onResetProgress = {},
        )
    }
}

@Preview
@Composable
private fun VocabularyListContentErrorPreview() {
    EvolaTheme {
        VocabularyListContent(
            state = VocabularyListState(content = VocabularyListContent.Error("Couldn't load your vocabulary")),
            snackbarHostState = remember { SnackbarHostState() },
            speechService = rememberSpeechService(),
            onBack = {},
            onSelectItem = {},
            onAddWord = {},
            onImportCsv = {},
            onResetProgress = {},
        )
    }
}
