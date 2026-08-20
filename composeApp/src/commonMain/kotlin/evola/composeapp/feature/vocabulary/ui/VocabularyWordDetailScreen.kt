package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import evola.composeapp.core.navigation.BackHandler
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_cancel
import evola.composeapp.generated.resources.lessons_action_copy_personal
import evola.composeapp.generated.resources.lessons_action_edit
import evola.composeapp.generated.resources.lessons_action_mark_known
import evola.composeapp.generated.resources.lessons_action_remove
import evola.composeapp.generated.resources.lessons_ipa_slash
import evola.composeapp.generated.resources.lessons_nav_back
import evola.composeapp.generated.resources.lessons_plural_prefix
import evola.composeapp.generated.resources.lessons_remove_word_text
import evola.composeapp.generated.resources.lessons_remove_word_title
import evola.composeapp.generated.resources.lessons_section_actions
import evola.composeapp.generated.resources.lessons_section_details
import evola.composeapp.generated.resources.lessons_section_example
import evola.composeapp.generated.resources.lessons_section_grammar
import evola.composeapp.generated.resources.lessons_section_meaning
import evola.composeapp.generated.resources.lessons_section_memory_tip
import evola.composeapp.generated.resources.lessons_vocab_status_introduced
import evola.composeapp.generated.resources.lessons_vocab_status_introduced_short
import evola.composeapp.generated.resources.lessons_vocab_status_learning
import evola.composeapp.generated.resources.lessons_vocab_status_learning_short
import evola.composeapp.generated.resources.lessons_vocab_status_mastered
import evola.composeapp.generated.resources.lessons_vocab_status_mastered_short
import evola.composeapp.generated.resources.lessons_vocab_status_new
import evola.composeapp.generated.resources.lessons_vocab_status_new_short
import evola.composeapp.generated.resources.lessons_vocab_status_review
import evola.composeapp.generated.resources.lessons_vocab_status_review_short
import evola.composeapp.generated.resources.lessons_word_details_title
import evola.composeapp.core.common.RtlText
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.VocabularyItem
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Maps the raw SRS status (unseen/introduced/learning/review/mastered - see VocabularySrs.STATUSES)
 * onto the same color scale [evola.composeapp.feature.home.ui.HomeScreen]'s word-breakdown cards already use
 * for the same tiers, so a learner sees one consistent color language for "where a word stands"
 * across the whole app. Shared between [VocabularyRow] (in `VocabularyListScreen.kt`) and this
 * screen's own [VocabularyStatusBadge]. */
@Composable
internal fun vocabularyStatusStyle(status: String): Pair<Color, String> = when (status) {
    "unseen" -> EvolaColors.Text3 to stringResource(Res.string.lessons_vocab_status_new_short)
    "introduced" -> EvolaColors.Ink2 to stringResource(Res.string.lessons_vocab_status_introduced_short)
    "learning" -> EvolaColors.Amber to stringResource(Res.string.lessons_vocab_status_learning_short)
    "review" -> EvolaColors.Rust to stringResource(Res.string.lessons_vocab_status_review_short)
    "mastered" -> EvolaColors.Teal to stringResource(Res.string.lessons_vocab_status_mastered_short)
    else -> EvolaColors.Text3 to status.uppercase()
}

/** Kept for the word-detail screen, which has room for an icon + label rather than the list row's
 * compact rail + caption. */
@Composable
private fun VocabularyStatusBadge(status: String) {
    val (icon, label) = when (status) {
        "unseen" -> Icons.Filled.RadioButtonUnchecked to stringResource(Res.string.lessons_vocab_status_new)
        "introduced" -> Icons.Filled.Circle to stringResource(Res.string.lessons_vocab_status_introduced)
        "learning" -> Icons.Filled.HourglassBottom to stringResource(Res.string.lessons_vocab_status_learning)
        "review" -> Icons.Filled.Replay to stringResource(Res.string.lessons_vocab_status_review)
        "mastered" -> Icons.Filled.CheckCircle to stringResource(Res.string.lessons_vocab_status_mastered)
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
private fun Tag(label: String, icon: ImageVector, tint: Color) {
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VocabularyWordDetailScreen(
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
            title = { Text(stringResource(Res.string.lessons_remove_word_title)) },
            text = { Text(stringResource(Res.string.lessons_remove_word_text, item.term)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text(stringResource(Res.string.lessons_action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(Res.string.lessons_action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_word_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_nav_back))
                    }
                },
                actions = { TextButton(onClick = onEdit) { Text(stringResource(Res.string.lessons_action_edit)) } },
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
                            Text(stringResource(Res.string.lessons_ipa_slash, it), style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                        }
                    }
                    val subLine = listOfNotNull(
                        item.partOfSpeech,
                        item.plural?.let { stringResource(Res.string.lessons_plural_prefix, it) },
                    ).joinToString(" · ")
                    if (subLine.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subLine, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                    }
                }
                VocabularyStatusBadge(item.status)
            }

            DetailSection(stringResource(Res.string.lessons_section_meaning)) {
                Text(item.meaning, style = MaterialTheme.typography.bodyLarge)
                item.nativeMeaning?.let {
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    RtlText(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                }
            }

            item.exampleSentence?.let { sentence ->
                DetailSection(stringResource(Res.string.lessons_section_example)) {
                    Text(sentence, style = MaterialTheme.typography.bodyLarge)
                    item.exampleSentenceTranslation?.let {
                        Spacer(Modifier.height(EvolaSpacing.xs))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                    }
                }
            }

            item.grammarNote?.let { DetailSection(stringResource(Res.string.lessons_section_grammar)) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            item.memoryTip?.let { DetailSection(stringResource(Res.string.lessons_section_memory_tip)) { Text(it, style = MaterialTheme.typography.bodyMedium) } }

            if (item.difficultyRating != null || item.frequencyRating != null || item.relatedWords.isNotEmpty()) {
                DetailSection(stringResource(Res.string.lessons_section_details)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                        item.difficultyRating?.let { Tag(it, Icons.Filled.Speed, EvolaColors.Gold) }
                        item.frequencyRating?.let { Tag(it, Icons.AutoMirrored.Filled.TrendingUp, EvolaColors.Ink2) }
                        item.relatedWords.forEach { Tag(it, Icons.Filled.Link, EvolaColors.Text2) }
                    }
                }
            }

            DetailSection(stringResource(Res.string.lessons_section_actions)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WordActionRow(Icons.Filled.CheckCircle, stringResource(Res.string.lessons_action_mark_known), onClick = onMarkAlreadyKnown)
                    WordActionRow(Icons.Filled.ContentCopy, stringResource(Res.string.lessons_action_copy_personal), onClick = onCopyToPersonalList)
                    WordActionRow(Icons.Filled.Delete, stringResource(Res.string.lessons_action_remove), onClick = { showDeleteConfirm = true }, tint = EvolaColors.Rust)
                }
            }
        }
    }
}

/** A single row in the word-detail sheet's "Actions" section - same icon+label shape as
 * [Tag]/`AppRow` elsewhere in this app, sized for a full-width tap target rather than a chip. */
@Composable
private fun WordActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = EvolaColors.Accent,
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

private val fakeWordDetailItem = VocabularyItem(
    itemId = "1",
    term = "Haus",
    gender = "das",
    meaning = "house",
    nativeMeaning = "بيت",
    status = "learning",
    ipaPronunciation = "haʊs",
    partOfSpeech = "noun",
    plural = "Häuser",
    exampleSentence = "Das Haus ist groß.",
    exampleSentenceTranslation = "The house is big.",
    grammarNote = "Neuter noun, takes 'das'.",
    memoryTip = "Sounds like English 'house'.",
    difficultyRating = "Easy",
    frequencyRating = "Common",
    relatedWords = listOf("Wohnung", "Gebäude"),
)

@Preview
@Composable
private fun VocabularyWordDetailScreenPreview() {
    EvolaTheme {
        VocabularyWordDetailScreen(
            item = fakeWordDetailItem,
            onBack = {},
            onEdit = {},
            onMarkAlreadyKnown = {},
            onCopyToPersonalList = {},
            onDelete = {},
        )
    }
}
