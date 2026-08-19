package evola.composeapp.onboarding

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import evola.composeapp.theme.EvolaSpacing
import evola.shared.goals.Goal
import evola.shared.language.NativeLanguage
import pro.respawn.flowmvi.compose.dsl.subscribe

private const val GOAL_TEXT_SOFT_CAP = 200

/** Goal Setup per 01_PRODUCT_SPEC.md §1.4 - freeform text only, no template picker. [nativeLanguage]
 * was already chosen on the preceding onboarding step and is saved atomically with the goal. */
@Composable
fun GoalSetupScreen(viewModel: GoalSetupViewModel, nativeLanguage: NativeLanguage, onGoalCreated: (Goal) -> Unit) {
    val state by viewModel.subscribe()
    val isSubmitting = state.isSubmitting
    val errorMessage = state.errorMessage
    var goalText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var wasTruncated by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.goalCreated?.id) {
        state.goalCreated?.let { event -> onGoalCreated(event.goal) }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
            // Tapping anywhere outside a text field dismisses the keyboard - without this, on a
            // screen with no other focusable target, there was no way to get the keyboard out of
            // the way to reach "Start learning" underneath it.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("What are you working toward?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                "Describe your goal in your own words - we'll build lessons around it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            OutlinedTextField(
                value = goalText,
                onValueChange = {
                    if (it.length > GOAL_TEXT_SOFT_CAP) {
                        goalText = it.take(GOAL_TEXT_SOFT_CAP)
                        wasTruncated = true
                    } else {
                        goalText = it
                        wasTruncated = false
                    }
                },
                label = { Text("Your goal") },
                placeholder = { Text("e.g. Pass the German B1 exam") },
                enabled = !isSubmitting,
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (wasTruncated) {
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(
                    "Goal text is capped at $GOAL_TEXT_SOFT_CAP characters - trimmed to fit.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(EvolaSpacing.md))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 60) title = it },
                label = { Text("Name your journey (optional)") },
                placeholder = { Text("Leave blank to auto-generate") },
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            errorMessage?.let {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(EvolaSpacing.xl))
            Button(
                onClick = { viewModel.intent(GoalSetupIntent.CreateGoal(goalText, title, nativeLanguage)) },
                enabled = !isSubmitting && goalText.trim().length >= 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSubmitting) "Saving..." else "Start learning")
            }
        }
    }
}
