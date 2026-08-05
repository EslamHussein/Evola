package evola.composeapp.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.KEY_ANTHROPIC_API_KEY
import evola.composeapp.rememberSecureStore
import evola.shared.auth.AuthUser
import evola.shared.goals.Goal

/** Profile tab per 06_SCREENS_REFERENCE.md - account info + goal editing + sign-out.
 * Notifications/subscription/privacy are explicitly out of MVP scope (01_PRODUCT_SPEC.md), so
 * they aren't stubbed here at all rather than shown as fake placeholders. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AuthUser,
    goal: Goal,
    viewModel: ProfileViewModel,
    onGoalUpdated: (Goal) -> Unit,
    onLogout: () -> Unit,
) {
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var isEditingGoal by remember { mutableStateOf(false) }
    var goalText by remember(goal.id) { mutableStateOf(goal.goalText) }
    var title by remember(goal.id) { mutableStateOf(goal.title ?: "") }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(user.fullName, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(32.dp))
                Text("Your goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isEditingGoal) {
                            OutlinedTextField(
                                value = goalText,
                                onValueChange = { if (it.length <= 200) goalText = it },
                                label = { Text("Goal") },
                                enabled = !isSubmitting,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 60) title = it },
                                label = { Text("Journey title") },
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            errorMessage?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                TextButton(onClick = { isEditingGoal = false }, enabled = !isSubmitting) { Text("Cancel") }
                                Button(
                                    onClick = {
                                        viewModel.updateGoal(goal.id, goalText, title) { updated ->
                                            onGoalUpdated(updated)
                                            isEditingGoal = false
                                        }
                                    },
                                    enabled = !isSubmitting && goalText.trim().length >= 3,
                                ) {
                                    Text(if (isSubmitting) "Saving..." else "Save")
                                }
                            }
                        } else {
                            Text(goal.title ?: "Your journey", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(goal.goalText, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { isEditingGoal = true }) { Text("Edit goal") }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                AnthropicKeySection()

                Spacer(Modifier.height(32.dp))
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign out")
                }
            }
        }
    }
}

/** On-device Anthropic API key entry — Evola calls Claude directly (serverless), so the key lives
 * in the device's encrypted store, never on a server. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AnthropicKeySection() {
    val secureStore = rememberSecureStore()
    var savedKey by remember { mutableStateOf(secureStore.get(KEY_ANTHROPIC_API_KEY)) }
    var draft by remember { mutableStateOf("") }

    Text("AI (Claude) key", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (savedKey.isNullOrBlank()) {
                    "No key set. Evola needs your Anthropic API key to generate lessons on this device."
                } else {
                    "A key is saved on this device. Enter a new one to replace it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Anthropic API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (!savedKey.isNullOrBlank()) {
                    TextButton(onClick = {
                        secureStore.remove(KEY_ANTHROPIC_API_KEY)
                        savedKey = null
                        draft = ""
                    }) { Text("Clear") }
                }
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        secureStore.put(KEY_ANTHROPIC_API_KEY, trimmed)
                        savedKey = trimmed
                        draft = ""
                    },
                    enabled = draft.trim().isNotEmpty(),
                ) { Text("Save key") }
            }
        }
    }
}
