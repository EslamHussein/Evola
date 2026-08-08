@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.language.LocalNativeLanguage
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.VocabularyItem

@Composable
fun VocabularyListScreen(viewModel: VocabularyListViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingItem by remember { mutableStateOf<VocabularyItem?>(null) }

    editingItem?.let { item ->
        EditVocabularyDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { term, meaning, nativeMeaning ->
                viewModel.updateItem(item.itemId, term, meaning, nativeMeaning)
                editingItem = null
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is VocabularyListState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChaseLoadingIndicator()
                }

                is VocabularyListState.Error -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is VocabularyListState.Loaded -> {
                    if (current.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No vocabulary yet for this lesson.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(current.items) { item -> VocabularyRow(item, onClick = { editingItem = item }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabularyRow(item: VocabularyItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.term, style = MaterialTheme.typography.titleMedium)
                        item.ipaPronunciation?.let {
                            Text("/$it/", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                }
                VocabularyStatusBadge(item.status)
            }

            // The learner's chosen native-language translation - falls back to nothing (the
            // English meaning above already covers items with no native translation yet) rather
            // than a duplicate/confusing second English line.
            item.nativeMeaning?.let {
                Spacer(Modifier.height(EvolaSpacing.xs))
                RtlText(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (item.difficultyRating != null || item.frequencyRating != null || item.relatedWords.isNotEmpty()) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                    item.difficultyRating?.let { Tag(it) }
                    item.frequencyRating?.let { Tag(it) }
                    item.relatedWords.forEach { Tag(it) }
                }
            }

            item.memoryTip?.let {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            }
        }
    }
}

/** Maps the raw SRS status (unseen/introduced/learning/review/mastered - see VocabularySrs.STATUSES)
 * onto the same ascending icon+color scale as the session's ladder progress, so a learner can
 * connect "which step am I doing" (in the session) to "where does this word actually stand" (here)
 * instead of the two screens using two unrelated vocabularies for one journey. */
@Composable
private fun VocabularyStatusBadge(status: String) {
    val (icon, color, label) = when (status) {
        "unseen" -> Triple(Icons.Filled.RadioButtonUnchecked, EvolaColors.Text3, "New")
        "introduced" -> Triple(Icons.Filled.Circle, EvolaColors.Text2, "Introduced")
        "learning" -> Triple(Icons.Filled.HourglassBottom, EvolaColors.Ink2, "Learning")
        "review" -> Triple(Icons.Filled.Replay, EvolaColors.Ink2, "Review")
        "mastered" -> Triple(Icons.Filled.CheckCircle, EvolaColors.Accent, "Mastered")
        else -> Triple(Icons.Filled.RadioButtonUnchecked, EvolaColors.Text3, status.replaceFirstChar { it.uppercase() })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun Tag(label: String) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = EvolaColors.Text2,
        )
    }
}

@Composable
private fun EditVocabularyDialog(
    item: VocabularyItem,
    onDismiss: () -> Unit,
    onSave: (term: String, meaning: String, nativeMeaning: String?) -> Unit,
) {
    var term by remember(item.itemId) { mutableStateOf(item.term) }
    var meaning by remember(item.itemId) { mutableStateOf(item.meaning) }
    var nativeMeaning by remember(item.itemId) { mutableStateOf(item.nativeMeaning ?: "") }
    val nativeLanguage = LocalNativeLanguage.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Edit word") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text("Meaning") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nativeMeaning,
                    onValueChange = { nativeMeaning = it },
                    label = { Text("${nativeLanguage.englishName} meaning (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(term.trim(), meaning.trim(), nativeMeaning.trim().ifBlank { null }) },
                enabled = term.isNotBlank() && meaning.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
