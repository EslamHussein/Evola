package evola.composeapp.feature.vocabulary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import evola.composeapp.core.common.LocalNativeLanguage
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.designsystem.components.EvolaButtonSize
import evola.composeapp.core.designsystem.components.EvolaPrimaryButton
import evola.composeapp.core.navigation.BackHandler
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_cancel
import evola.composeapp.generated.resources.lessons_action_save
import evola.composeapp.generated.resources.lessons_edit_word_title
import evola.composeapp.generated.resources.lessons_field_native_meaning_optional
import evola.composeapp.generated.resources.lessons_field_term
import evola.composeapp.generated.resources.lessons_section_meaning
import evola.shared.feature.vocabulary.domain.VocabularyItem
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** Full screen rather than a popup dialog - editing needs enough room for the on-screen keyboard
 * plus three fields (one of them RTL-aware) without the dialog's cramped fixed height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditVocabularyScreen(
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
                title = { Text(stringResource(Res.string.lessons_edit_word_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_action_cancel))
                    }
                },
                actions = {
                    EvolaPrimaryButton(
                        text = stringResource(Res.string.lessons_action_save),
                        onClick = { onSave(term.trim(), meaning.trim(), nativeMeaning.trim().ifBlank { null }) },
                        enabled = canSave,
                        size = EvolaButtonSize.Small,
                        modifier = Modifier.padding(end = EvolaSpacing.sm),
                    )
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
                label = { Text(stringResource(Res.string.lessons_field_term)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = meaning,
                onValueChange = { meaning = it },
                label = { Text(stringResource(Res.string.lessons_section_meaning)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nativeMeaning,
                onValueChange = { nativeMeaning = it },
                label = { Text(stringResource(Res.string.lessons_field_native_meaning_optional, nativeLanguage.englishName)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val fakeEditVocabularyItem = VocabularyItem(
    itemId = "1",
    term = "Haus",
    meaning = "house",
    nativeMeaning = "بيت",
    status = "learning",
)

@Preview
@Composable
private fun EditVocabularyScreenPreview() {
    EvolaTheme {
        EditVocabularyScreen(item = fakeEditVocabularyItem, onBack = {}, onSave = { _, _, _ -> })
    }
}
