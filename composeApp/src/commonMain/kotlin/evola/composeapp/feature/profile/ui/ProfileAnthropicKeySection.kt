package evola.composeapp.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import evola.composeapp.KEY_ANTHROPIC_API_KEY
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_profile_ai_key_title
import evola.composeapp.generated.resources.main_profile_api_key_field_label
import evola.composeapp.generated.resources.main_profile_api_key_row_title
import evola.composeapp.generated.resources.main_profile_cancel
import evola.composeapp.generated.resources.main_profile_clear_key
import evola.composeapp.generated.resources.main_profile_key_connected
import evola.composeapp.generated.resources.main_profile_key_malformed_hint
import evola.composeapp.generated.resources.main_profile_key_needed_body
import evola.composeapp.generated.resources.main_profile_key_not_set
import evola.composeapp.generated.resources.main_profile_key_removed_snackbar
import evola.composeapp.generated.resources.main_profile_key_replace_body
import evola.composeapp.generated.resources.main_profile_key_saved_snackbar
import evola.composeapp.generated.resources.main_profile_remove_key_confirm
import evola.composeapp.generated.resources.main_profile_remove_key_dialog_body
import evola.composeapp.generated.resources.main_profile_remove_key_dialog_title
import evola.composeapp.generated.resources.main_profile_save_key
import evola.composeapp.generated.resources.main_profile_version_footer
import evola.composeapp.rememberSecureStore
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.components.EvolaDestructiveGhostButton
import evola.composeapp.core.designsystem.components.IconTile
import evola.composeapp.core.designsystem.components.StatusTag
import evola.composeapp.core.designsystem.components.StatusTagStyle
import evola.composeapp.core.designsystem.EvolaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/** On-device Anthropic API key entry — Evola calls Claude directly (serverless), so the key lives
 * in the device's encrypted store, never on a server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnthropicKeySection(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
) {
    val secureStore = rememberSecureStore()
    var savedKey by remember { mutableStateOf(secureStore.get(KEY_ANTHROPIC_API_KEY)) }
    var draft by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val looksMalformed = draft.isNotBlank() && !draft.trim().startsWith("sk-ant-")
    val isConnected = !savedKey.isNullOrBlank()

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(Res.string.main_profile_remove_key_dialog_title)) },
            text = { Text(stringResource(Res.string.main_profile_remove_key_dialog_body)) },
            confirmButton = {
                val keyRemovedMessage = stringResource(Res.string.main_profile_key_removed_snackbar)
                EvolaDestructiveGhostButton(
                    text = stringResource(Res.string.main_profile_remove_key_confirm),
                    onClick = {
                        secureStore.remove(KEY_ANTHROPIC_API_KEY)
                        savedKey = null
                        draft = ""
                        showClearConfirm = false
                        coroutineScope.launch { snackbarHostState.showSnackbar(keyRemovedMessage) }
                    },
                )
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(Res.string.main_profile_cancel)) } },
        )
    }

    Text(
        stringResource(Res.string.main_profile_ai_key_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(EvolaSpacing.sm))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.Key, locked = !isConnected)
                Spacer(Modifier.width(EvolaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.main_profile_api_key_row_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    StatusTag(
                        stringResource(if (isConnected) Res.string.main_profile_key_connected else Res.string.main_profile_key_not_set),
                        if (isConnected) StatusTagStyle.FILLED else StatusTagStyle.NEUTRAL,
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))
            Text(
                stringResource(if (!isConnected) Res.string.main_profile_key_needed_body else Res.string.main_profile_key_replace_body),
                style = MaterialTheme.typography.bodySmall,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(Res.string.main_profile_api_key_field_label)) },
                singleLine = true,
                isError = looksMalformed,
                supportingText = if (looksMalformed) {
                    { Text(stringResource(Res.string.main_profile_key_malformed_hint)) }
                } else {
                    null
                },
                // No PasswordVisualTransformation: on iOS, Compose Multiplatform disables the
                // paste/text-actions menu entirely on any masked field (JetBrains/compose-
                // multiplatform#4502) - since pasting a copied key is the actual workflow here,
                // masking would break the field's main use case. The key isn't shown anywhere
                // else once saved (draft clears after save; only "Connected"/"Not set" is shown).
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(EvolaSpacing.md))
            val keySavedMessage = stringResource(Res.string.main_profile_key_saved_snackbar)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm, Alignment.End)) {
                if (isConnected) {
                    TextButton(onClick = { showClearConfirm = true }) { Text(stringResource(Res.string.main_profile_clear_key)) }
                }
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        secureStore.put(KEY_ANTHROPIC_API_KEY, trimmed)
                        savedKey = trimmed
                        draft = ""
                        focusManager.clearFocus()
                        coroutineScope.launch { snackbarHostState.showSnackbar(keySavedMessage) }
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) { Text(stringResource(Res.string.main_profile_save_key)) }
            }
        }
    }

    Spacer(Modifier.height(EvolaSpacing.xxl))
    Text(
        stringResource(Res.string.main_profile_version_footer),
        style = MaterialTheme.typography.labelSmall,
        color = EvolaColors.Text3,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun AnthropicKeySectionPreview() {
    EvolaTheme {
        Column {
            AnthropicKeySection(
                snackbarHostState = remember { SnackbarHostState() },
                coroutineScope = rememberCoroutineScope(),
            )
        }
    }
}
