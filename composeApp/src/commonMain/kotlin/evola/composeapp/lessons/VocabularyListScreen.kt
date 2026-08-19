@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import evola.composeapp.loading.ChaseLoadingIndicator
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import evola.composeapp.BackHandler
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.rtl.RtlText
import evola.composeapp.speech.rememberSpeechService
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.vocabulary.rememberCsvFilePicker
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.parseWordCsv
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.compose.dsl.subscribe

private enum class VocabularySortMode(val label: String) {
    DEFAULT("Default"),
    ALPHABETICAL("A–Z"),
    PROGRESS("Progress"),
}

@Composable
fun VocabularyListScreen(viewModel: VocabularyListViewModel, onBack: () -> Unit) {
    val state by viewModel.subscribe()
    var editingItem by remember { mutableStateOf<VocabularyItem?>(null) }
    var viewingItem by remember { mutableStateOf<VocabularyItem?>(null) }
    var addingWord by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(VocabularySortMode.DEFAULT) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset progress?") },
            text = { Text("Every word in this lesson goes back to \"New\". This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showResetConfirm = false; viewModel.intent(VocabularyListIntent.ResetProgress) }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.event?.id) {
        when (val event = state.event) {
            is VocabularyListEvent.ItemUpdated -> Unit
            is VocabularyListEvent.ItemUpdateFailed -> snackbarHostState.showSnackbar("Couldn't save that change")
            is VocabularyListEvent.MarkedAlreadyKnown -> viewingItem = null
            is VocabularyListEvent.CopiedToPersonalList -> viewingItem = null
            is VocabularyListEvent.WordAdded -> addingWord = false
            is VocabularyListEvent.WordsImported -> {
                val message = if (event.count == null) "Import failed" else "Imported ${event.count} word${if (event.count == 1) "" else "s"}"
                snackbarHostState.showSnackbar(message)
            }
            is VocabularyListEvent.ItemDeleted -> {
                viewingItem = null
                if (!event.success) snackbarHostState.showSnackbar("Couldn't remove that word")
            }
            is VocabularyListEvent.ProgressReset -> {
                snackbarHostState.showSnackbar(if (event.success) "Progress reset" else "Couldn't reset progress")
            }
            null -> Unit
        }
    }

    editingItem?.let { item ->
        EditVocabularyScreen(
            item = item,
            onBack = { editingItem = null },
            onSave = { term, meaning, nativeMeaning ->
                viewModel.intent(VocabularyListIntent.UpdateItem(item.itemId, term, meaning, nativeMeaning))
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
            onMarkAlreadyKnown = { viewModel.intent(VocabularyListIntent.MarkAlreadyKnown(item.itemId)) },
            onCopyToPersonalList = { viewModel.intent(VocabularyListIntent.CopyToPersonalList(item.itemId)) },
            onDelete = { viewModel.intent(VocabularyListIntent.DeleteItem(item.itemId)) },
        )
        return
    }

    if (addingWord) {
        AddVocabularyScreen(
            onBack = { addingWord = false },
            onSave = { term, meaning, nativeMeaning -> viewModel.intent(VocabularyListIntent.AddWord(term, meaning, nativeMeaning)) },
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val speechService = rememberSpeechService()
    val importCsv = rememberCsvFilePicker { text ->
        val rows = parseWordCsv(text)
        if (rows.isEmpty()) {
            coroutineScope.launch { snackbarHostState.showSnackbar("No valid rows found in that file") }
        } else {
            viewModel.intent(VocabularyListIntent.ImportWords(rows))
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Vocabulary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            VocabularySortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
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
                    TextButton(onClick = importCsv) { Text("IMPORT") }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Reset progress") },
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
                onClick = { addingWord = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Word") },
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

                is VocabularyListContent.Error -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is VocabularyListContent.Loaded -> {
                    if (current.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No vocabulary yet for this lesson.",
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
                                placeholder = { Text("Search for words…") },
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
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No words match \"$query\".", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
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
                                            onClick = { viewingItem = item },
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
                    Text("/$it/", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
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
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play pronunciation", tint = EvolaColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Maps the raw SRS status (unseen/introduced/learning/review/mastered - see VocabularySrs.STATUSES)
 * onto the same color scale [HomeScreen]'s word-breakdown cards already use for the same tiers, so
 * a learner sees one consistent color language for "where a word stands" across the whole app. */
@Composable
private fun vocabularyStatusStyle(status: String): Pair<Color, String> = when (status) {
    "unseen" -> EvolaColors.Text3 to "NEW"
    "introduced" -> EvolaColors.Ink2 to "INTRODUCED"
    "learning" -> EvolaColors.Amber to "LEARNING"
    "review" -> EvolaColors.Rust to "REVIEW"
    "mastered" -> EvolaColors.Teal to "MASTERED"
    else -> EvolaColors.Text3 to status.uppercase()
}

/** Kept for the word-detail screen, which has room for an icon + label rather than the list row's
 * compact rail + caption. */
@Composable
private fun VocabularyStatusBadge(status: String) {
    val (icon, label) = when (status) {
        "unseen" -> Icons.Filled.RadioButtonUnchecked to "New"
        "introduced" -> Icons.Filled.Circle to "Introduced"
        "learning" -> Icons.Filled.HourglassBottom to "Learning"
        "review" -> Icons.Filled.Replay to "Review"
        "mastered" -> Icons.Filled.CheckCircle to "Mastered"
        else -> Icons.Filled.RadioButtonUnchecked to status.replaceFirstChar { it.uppercase() }
    }
    val (color, _) = vocabularyStatusStyle(status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** [icon]/[tint] tell difficulty, frequency, and related-word chips apart at a glance - previously
 * all three rendered identically, so a learner had no way to tell what a given pill meant without
 * reading its text and guessing from context. */
@Composable
private fun Tag(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
        Row(
            modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text2)
        }
    }
}

/** Read-only "everything about this word" screen, reached by tapping a row - the row itself only
 * has room for a handful of fields, and grammar_note/example_sentence_translation aren't shown
 * anywhere in the list at all. Full screen rather than a dialog, matching EditVocabularyScreen's
 * own choice for the same reason (room to breathe, no cramped fixed height). */
@Composable
private fun VocabularyWordDetailScreen(
    item: VocabularyItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMarkAlreadyKnown: () -> Unit,
    onCopyToPersonalList: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove this word?") },
            text = { Text("Are you sure you want to remove \"${item.term}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onEdit) { Text("Edit") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(EvolaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            item.gender?.let { "$it ${item.term}" } ?: item.term,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        item.ipaPronunciation?.let {
                            Text("/$it/", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                        }
                    }
                    val subLine = listOfNotNull(item.partOfSpeech, item.plural?.let { "plural: $it" }).joinToString(" · ")
                    if (subLine.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subLine, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                    }
                }
                VocabularyStatusBadge(item.status)
            }

            DetailSection("Meaning") {
                Text(item.meaning, style = MaterialTheme.typography.bodyLarge)
                item.nativeMeaning?.let {
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    RtlText(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                }
            }

            item.exampleSentence?.let { sentence ->
                DetailSection("Example") {
                    Text(sentence, style = MaterialTheme.typography.bodyLarge)
                    item.exampleSentenceTranslation?.let {
                        Spacer(Modifier.height(EvolaSpacing.xs))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                    }
                }
            }

            item.grammarNote?.let { DetailSection("Grammar") { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            item.memoryTip?.let { DetailSection("Memory tip") { Text(it, style = MaterialTheme.typography.bodyMedium) } }

            if (item.difficultyRating != null || item.frequencyRating != null || item.relatedWords.isNotEmpty()) {
                DetailSection("Details") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                        item.difficultyRating?.let { Tag(it, Icons.Filled.Speed, EvolaColors.Gold) }
                        item.frequencyRating?.let { Tag(it, Icons.AutoMirrored.Filled.TrendingUp, EvolaColors.Ink2) }
                        item.relatedWords.forEach { Tag(it, Icons.Filled.Link, EvolaColors.Text2) }
                    }
                }
            }

            DetailSection("Actions") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WordActionRow(Icons.Filled.CheckCircle, "Mark as already known", onClick = onMarkAlreadyKnown)
                    WordActionRow(Icons.Filled.ContentCopy, "Copy to \"Eigene Vokabeln\"", onClick = onCopyToPersonalList)
                    WordActionRow(Icons.Filled.Delete, "Remove", onClick = { showDeleteConfirm = true }, tint = EvolaColors.Rust)
                }
            }
        }
    }
}

/** A single row in the word-detail sheet's "Actions" section - same icon+label shape as
 * [Tag]/[AppRow] elsewhere in this app, sized for a full-width tap target rather than a chip. */
@Composable
private fun WordActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = EvolaColors.Accent,
) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = EvolaColors.SurfaceAlt, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(EvolaSpacing.xs))
        content()
    }
}

/** Reword-style "add your own word" - same shape as [EditVocabularyScreen] but starting from blank
 * fields and landing in whichever lesson is currently open (Evola's content is lesson-scoped, so
 * there's no separate "personal" deck the way Reword's "Eigene Vokabeln" list is). */
@Composable
private fun AddVocabularyScreen(onBack: () -> Unit, onSave: (term: String, meaning: String, nativeMeaning: String?) -> Unit) {
    var term by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var nativeMeaning by remember { mutableStateOf("") }
    val nativeLanguage = LocalNativeLanguage.current
    val canSave = term.isNotBlank() && meaning.isNotBlank()
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add word") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(term.trim(), meaning.trim(), nativeMeaning.trim().ifBlank { null }) },
                        enabled = canSave,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusManager.clearFocus() }
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(EvolaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
        ) {
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                label = { Text("Term") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it },
                label = { Text("Meaning") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nativeMeaning,
                onValueChange = { nativeMeaning = it },
                label = { Text("${nativeLanguage.englishName} meaning (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Full screen rather than a popup dialog - editing needs enough room for the on-screen keyboard
 * plus three fields (one of them RTL-aware) without the dialog's cramped fixed height. */
@Composable
private fun EditVocabularyScreen(
    item: VocabularyItem,
    onBack: () -> Unit,
    onSave: (term: String, meaning: String, nativeMeaning: String?) -> Unit,
) {
    var term by remember(item.itemId) { mutableStateOf(item.term) }
    var meaning by remember(item.itemId) { mutableStateOf(item.meaning) }
    var nativeMeaning by remember(item.itemId) { mutableStateOf(item.nativeMeaning ?: "") }
    val nativeLanguage = LocalNativeLanguage.current
    val canSave = term.isNotBlank() && meaning.isNotBlank()
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit word") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(term.trim(), meaning.trim(), nativeMeaning.trim().ifBlank { null }) },
                        enabled = canSave,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                // Tapping anywhere outside a text field dismisses the keyboard.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusManager.clearFocus() }
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(EvolaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.md),
        ) {
            OutlinedTextField(
                value = term,
                onValueChange = { term = it },
                label = { Text("Term") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it },
                label = { Text("Meaning") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nativeMeaning,
                onValueChange = { nativeMeaning = it },
                label = { Text("${nativeLanguage.englishName} meaning (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
